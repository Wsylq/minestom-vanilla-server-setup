import json
import os
import sqlite3
from datetime import datetime, timezone

from flask import Flask, jsonify, request


DB_PATH = os.getenv("DB_PATH", "sync.db")
AUTH_TOKEN = os.getenv("AUTH_TOKEN", "")

app = Flask(__name__)

init_db_called = False


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_db() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def init_db() -> None:
    with get_db() as db:
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS server_snapshots (
                server_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS player_snapshots (
                server_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (server_id, player_id)
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS block_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cluster_id TEXT NOT NULL,
                source_server_id TEXT NOT NULL,
                dimension TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                block TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS chat_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cluster_id TEXT NOT NULL,
                source_server_id TEXT NOT NULL,
                username TEXT NOT NULL,
                message TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )


@app.before_request
def ensure_db_ready():
    global init_db_called
    if not init_db_called:
        init_db()
        init_db_called = True


def check_auth() -> bool:
    if not AUTH_TOKEN:
        return True
    return request.headers.get("X-Auth-Token", "") == AUTH_TOKEN


@app.get("/health")
def health():
    return {"ok": True, "time": utc_now()}


@app.post("/api/server-state")
def save_server_state():
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "invalid json body"}), 400

    server_id = str(payload.get("serverId", "default")).strip() or "default"
    serialized_payload = json.dumps(payload, separators=(",", ":"))
    now = utc_now()

    with get_db() as db:
        db.execute(
            """
            INSERT INTO server_snapshots(server_id, payload, updated_at)
            VALUES(?, ?, ?)
            ON CONFLICT(server_id) DO UPDATE SET
                payload = excluded.payload,
                updated_at = excluded.updated_at
            """,
            (server_id, serialized_payload, now),
        )

        players = payload.get("players", [])
        if isinstance(players, list):
            for player in players:
                if not isinstance(player, dict):
                    continue
                player_id = str(player.get("username") or player.get("uuid") or "").strip()
                if not player_id:
                    continue
                db.execute(
                    """
                    INSERT INTO player_snapshots(server_id, player_id, payload, updated_at)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(server_id, player_id) DO UPDATE SET
                        payload = excluded.payload,
                        updated_at = excluded.updated_at
                    """,
                    (
                        server_id,
                        player_id,
                        json.dumps(player, separators=(",", ":")),
                        now,
                    ),
                )

    return {"ok": True, "serverId": server_id, "updatedAt": now}


@app.get("/api/server-state/<server_id>")
def get_server_state(server_id: str):
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    with get_db() as db:
        row = db.execute(
            "SELECT payload, updated_at FROM server_snapshots WHERE server_id = ?",
            (server_id,),
        ).fetchone()

    if row is None:
        return jsonify({"error": "not found"}), 404

    payload = json.loads(row["payload"])
    payload["updatedAt"] = row["updated_at"]
    return jsonify(payload)


@app.get("/api/player-state/<server_id>/<player_id>")
def get_player_state(server_id: str, player_id: str):
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    with get_db() as db:
        row = db.execute(
            """
            SELECT payload, updated_at
            FROM player_snapshots
            WHERE server_id = ? AND player_id = ?
            """,
            (server_id, player_id),
        ).fetchone()

    if row is None:
        return jsonify({"error": "not found"}), 404

    payload = json.loads(row["payload"])
    payload["updatedAt"] = row["updated_at"]
    return jsonify(payload)


@app.post("/api/block-update")
def save_block_update():
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "invalid json body"}), 400

    cluster_id = str(payload.get("clusterId", "default")).strip() or "default"
    source_server_id = str(payload.get("sourceServerId", "unknown")).strip() or "unknown"
    dimension = str(payload.get("dimension", "overworld")).strip() or "overworld"
    block = str(payload.get("block", "minecraft:air")).strip() or "minecraft:air"

    try:
        x = int(payload.get("x", 0))
        y = int(payload.get("y", 0))
        z = int(payload.get("z", 0))
    except (TypeError, ValueError):
        return jsonify({"error": "invalid coordinates"}), 400

    now = utc_now()
    with get_db() as db:
        cursor = db.execute(
            """
            INSERT INTO block_events(cluster_id, source_server_id, dimension, x, y, z, block, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (cluster_id, source_server_id, dimension, x, y, z, block, now),
        )
        event_id = cursor.lastrowid

    return {"ok": True, "eventId": event_id}


@app.get("/api/block-updates/<cluster_id>")
def get_block_updates(cluster_id: str):
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    try:
        since_id = int(request.args.get("since_id", "0"))
    except ValueError:
        since_id = 0

    with get_db() as db:
        rows = db.execute(
            """
            SELECT id, source_server_id, dimension, x, y, z, block
            FROM block_events
            WHERE cluster_id = ? AND id > ?
            ORDER BY id ASC
            LIMIT 500
            """,
            (cluster_id, since_id),
        ).fetchall()

    latest = since_id
    lines = []
    for row in rows:
        latest = max(latest, row["id"])
        safe_block = str(row["block"]).replace("|", "%7C").replace("\n", "%0A")
        lines.append(
            f"event:{row['id']}|{row['source_server_id']}|{row['dimension']}|{row['x']}|{row['y']}|{row['z']}|{safe_block}"
        )

    response_text = "\n".join([f"next_since:{latest}", *lines])
    return response_text, 200, {"Content-Type": "text/plain; charset=utf-8"}


@app.post("/api/chat-message")
def save_chat_message():
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "invalid json body"}), 400

    cluster_id = str(payload.get("clusterId", "default")).strip() or "default"
    source_server_id = str(payload.get("sourceServerId", "unknown")).strip() or "unknown"
    username = str(payload.get("username", "")).strip()
    message = str(payload.get("message", "")).strip()
    if not username or not message:
        return jsonify({"error": "username and message required"}), 400

    now = utc_now()
    with get_db() as db:
        cursor = db.execute(
            """
            INSERT INTO chat_events(cluster_id, source_server_id, username, message, created_at)
            VALUES(?, ?, ?, ?, ?)
            """,
            (cluster_id, source_server_id, username, message, now),
        )
        event_id = cursor.lastrowid

    return {"ok": True, "eventId": event_id}


@app.get("/api/chat-feed/<cluster_id>")
def get_chat_feed(cluster_id: str):
    if not check_auth():
        return jsonify({"error": "unauthorized"}), 401

    try:
        since_id = int(request.args.get("since_id", "0"))
    except ValueError:
        since_id = 0

    with get_db() as db:
        rows = db.execute(
            """
            SELECT id, source_server_id, username, message
            FROM chat_events
            WHERE cluster_id = ? AND id > ?
            ORDER BY id ASC
            LIMIT 500
            """,
            (cluster_id, since_id),
        ).fetchall()

    latest = since_id
    lines = []
    for row in rows:
        latest = max(latest, row["id"])
        safe_username = str(row["username"]).replace("|", "%7C").replace("\n", "%0A")
        safe_message = str(row["message"]).replace("|", "%7C").replace("\n", "%0A")
        lines.append(f"event:{row['id']}|{row['source_server_id']}|{safe_username}|{safe_message}")

    response_text = "\n".join([f"next_since:{latest}", *lines])
    return response_text, 200, {"Content-Type": "text/plain; charset=utf-8"}


if __name__ == "__main__":
    init_db()
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))
    app.run(host=host, port=port)
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


if __name__ == "__main__":
    init_db()
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))
    app.run(host=host, port=port)
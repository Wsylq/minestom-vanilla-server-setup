export default function App() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#090d14] text-slate-100">
      <div className="absolute inset-0 hero-grid opacity-30" />
      <div className="absolute inset-0 aurora-shift" />

      <main className="relative mx-auto flex min-h-screen max-w-5xl items-center px-6 py-20 sm:px-10">
        <div className="space-y-6">
          <p className="tracking-[0.22em] text-cyan-300 uppercase">Minestom Starter</p>
          <h1 className="max-w-2xl text-4xl leading-tight font-semibold tracking-tight sm:text-6xl">
            Vanilla-like Minecraft server code is now scaffolded in this project.
          </h1>
          <p className="max-w-xl text-base text-slate-300 sm:text-lg">
            Build with Maven on your machine using the generated Java source and pom.xml. This page exists only as a
            project status surface.
          </p>
          <div className="flex flex-wrap gap-3 text-sm">
            <span className="border border-slate-700 bg-slate-950/60 px-4 py-2">Entry: src/main/java/com/example/minestom/Main.java</span>
            <span className="border border-slate-700 bg-slate-950/60 px-4 py-2">Build: mvn clean package</span>
          </div>
        </div>
      </main>
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-48 bg-gradient-to-t from-[#090d14] to-transparent" />
    </div>
  );
}

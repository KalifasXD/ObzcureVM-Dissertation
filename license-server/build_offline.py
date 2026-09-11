# -*- coding: utf-8 -*-
"""Offline rebuild of the license-server: recompile SeedController against the deps already inside the
prebuilt Spring Boot fat jar, then swap the class back in preserving each entry's original compression
(so BOOT-INF/lib/*.jar stays STORED as the Spring Boot loader requires)."""
import os, sys, subprocess, zipfile, shutil, io, tempfile
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# BASE = the license-server module (this script lives at its root); no absolute path is hardcoded.
BASE = os.path.dirname(os.path.abspath(__file__))
SRC  = os.path.join(BASE, "src", "main", "java", "com", "dissertation", "license", "SeedController.java")
JAR  = os.path.join(BASE, "target", "license-server-0.1.0.jar")
# Scratch build area: a fresh OS temp directory, cleaned up on exit.
SCR  = tempfile.mkdtemp(prefix="lsvr_offline_")
ENTRY = "BOOT-INF/classes/com/dissertation/license/SeedController.class"

work = os.path.join(SCR, "lsvr_build")
shutil.rmtree(work, ignore_errors=True); os.makedirs(work)
with zipfile.ZipFile(JAR) as z:
    z.extractall(work)
classes = os.path.join(work, "BOOT-INF", "classes")
lib = os.path.join(work, "BOOT-INF", "lib")
cp = classes + ";" + os.path.join(lib, "*")

outdir = os.path.join(SCR, "lsvr_out"); shutil.rmtree(outdir, ignore_errors=True); os.makedirs(outdir)
r = subprocess.run(["javac", "--release", "17", "-parameters", "-cp", cp, "-d", outdir, SRC],
                   capture_output=True, text=True)
print("javac rc:", r.returncode)
if r.stdout.strip(): print("STDOUT:", r.stdout)
if r.stderr.strip(): print("STDERR:", r.stderr)
if r.returncode != 0:
    sys.exit(1)

newcls = os.path.join(outdir, "com", "dissertation", "license", "SeedController.class")
newbytes = open(newcls, "rb").read()
print("compiled SeedController.class:", len(newbytes), "bytes")

bak = JAR + ".prebak"
if not os.path.exists(bak):
    shutil.copy2(JAR, bak); print("backed up original ->", bak)

tmp = JAR + ".new"
replaced = False
with zipfile.ZipFile(JAR) as zin, zipfile.ZipFile(tmp, "w") as zout:
    for it in zin.infolist():
        if it.filename == ENTRY:
            data = newbytes; replaced = True
        else:
            data = zin.read(it.filename)
        zi = zipfile.ZipInfo(it.filename, date_time=it.date_time)
        zi.compress_type = it.compress_type      # preserve STORED for nested jars
        zi.external_attr = it.external_attr
        zi.internal_attr = it.internal_attr
        zi.create_system = it.create_system
        zout.writestr(zi, data)
os.replace(tmp, JAR)
print("replaced entry:", replaced, "| rebuilt jar:", os.path.getsize(JAR), "bytes")

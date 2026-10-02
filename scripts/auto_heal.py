#!/usr/bin/env python3
import base64, os, re, subprocess, sys
from pathlib import Path
from openai import OpenAI
ROOT=Path(__file__).resolve().parents[1]
LOG=ROOT/"build-error.log"
MODEL=os.getenv("OPENAI_MODEL","gpt-5")
TOKEN=os.getenv("AUTOHEAL_TOKEN","")
BRANCH=os.getenv("GITHUB_REF_NAME","main")
MAX=int(os.getenv("MAX_AUTOHEAL_COMMITS","5"))
def run(c,out=False): return subprocess.run(c,cwd=ROOT,text=True,check=True,stdout=subprocess.PIPE if out else None,stderr=subprocess.STDOUT if out else None)
def main():
    if not os.getenv("OPENAI_API_KEY"): raise RuntimeError("OPENAI_API_KEY secret is missing.")
    n=sum(x.startswith("[auto-heal]") for x in run(["git","log","-n","20","--format=%s"],True).stdout.splitlines())
    if n>=MAX: raise RuntimeError(f"Auto-heal stopped after {n} attempts.")
    log=LOG.read_text(encoding="utf-8",errors="replace")[-60000:]
    matches=re.findall(r"([A-Za-z0-9_./-]+\\.(?:kt|kts|java|gradle|xml))(?::\\d+)?(?::\\d+)?",log)
    path=None
    for p in matches:
        q=(ROOT/p.lstrip("./")).resolve()
        if q.is_file() and ROOT.resolve() in q.parents: path=q.relative_to(ROOT).as_posix(); break
    if not path:
        for p in ["app/src/main/java/com/aldiandrew/duos/MainActivity.kt","app/build.gradle.kts","build.gradle.kts","settings.gradle.kts"]:
            if (ROOT/p).is_file(): path=p; break
    if not path: raise RuntimeError("No failing source/build file found.")
    fp=ROOT/path; source=fp.read_text(encoding="utf-8",errors="replace")
    if len(source)>100000: raise RuntimeError("Failing file is too large.")
    prompt=f"""Fix this Android build failure.
BUILD LOG:
{log}
FILE: {path}
{source}
Return ONLY the complete corrected file. No Markdown fences. No explanation. Do not omit unchanged sections. Preserve architecture and dependencies. Do not invent APIs. If editing this file cannot fix the failure, return it unchanged."""
    r=OpenAI(api_key=os.environ["OPENAI_API_KEY"]).responses.create(model=MODEL,input=prompt)
    fixed=r.output_text.strip()
    if not fixed or fixed.startswith("```") or fixed.endswith("```") or len(fixed)<max(40,len(source)//20): raise RuntimeError("AI returned invalid/suspicious file.")
    fixed+="\n"
    if fixed==source: raise RuntimeError("AI returned unchanged file.")
    fp.write_text(fixed,encoding="utf-8")
    run(["git","config","user.name","Duos Auto-Heal"]); run(["git","config","user.email","41898282+github-actions[bot]@users.noreply.github.com"])
    run(["git","add",path]); s=run(["git","status","--porcelain"],True).stdout
    if not s.strip(): raise RuntimeError("No change to commit.")
    run(["git","commit","-m",f"[auto-heal] fix {path}"])
    if not TOKEN: raise RuntimeError("AUTOHEAL_TOKEN secret is missing.")
    auth=base64.b64encode(f"x-access-token:{TOKEN}".encode()).decode()
    run(["git","-c",f"http.extraheader=AUTHORIZATION: basic {auth}","push","origin",f"HEAD:{BRANCH}"])
    print("Auto-heal pushed:",path)
if __name__=="__main__":
    try: main()
    except Exception as e: print("AUTO-HEAL ERROR:",e,file=sys.stderr); sys.exit(1)

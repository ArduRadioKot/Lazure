import json,re,subprocess,itertools,sys,os
import os
HERE=os.path.dirname(os.path.abspath(__file__))
BENCH=os.environ.get("BENCH_DIR",HERE)
adb=os.environ.get("ADB","adb")
log=subprocess.check_output([adb,"logcat","-d","-s","SPEECH_EVAL"],text=True)
res={}
for line in log.splitlines():
    m=re.search(r"SPEECH_EVAL: (\w+)\.wav \[(\w+)\]: ([\d.]+)s, (\d+) seg, spk=(\d+): (.*)$",line)
    if not m: continue
    name,label,sec,nseg,nspk,body=m.groups()
    segs=[]
    for part in body.split(" | "):
        mm=re.match(r"s(\d+) (\d+)-(\d+) ",part)
        if mm: segs.append((int(mm[1]),int(mm[2])/1000,int(mm[3])/1000))
    res[name]=(segs,float(sec))
tot_ok=tot_all=0
for name,(segs,sec) in sorted(res.items()):
    f=f"{BENCH}/{name}.json"
    if not os.path.exists(f): continue
    gt=json.load(open(f))
    spk_gt=sorted({g["speaker"] for g in gt}); spk_pr=sorted({s[0] for s in segs})
    # 50 ms grid over GT speech
    step=0.05; conf={}
    total=0
    for g in gt:
        t=g["start"]+0.1
        while t<g["end"]-0.1:
            lab=None
            for s in segs:
                if s[1]<=t<=s[2]: lab=s[0]; break
            conf[(g["speaker"],lab)]=conf.get((g["speaker"],lab),0)+step; total+=step; t+=step
    covered=sum(v for (g,l),v in conf.items() if l is not None)
    best=0; bm=None
    cand=spk_pr+[None]*len(spk_gt)
    for perm in set(itertools.permutations(cand,len(spk_gt))):
        sc=sum(conf.get((g,p),0) for g,p in zip(spk_gt,perm))
        if sc>best: best,bm=sc,perm
    # boundary error: for each GT turn start, nearest predicted segment start of that mapped speaker
    print(f"{name:8s} gt_spk={len(spk_gt)} pred_spk={len(spk_pr)} accuracy={100*best/total:5.1f}%  covered={100*covered/total:5.1f}%  label-acc-of-covered={100*best/max(covered,1e-9):5.1f}%  ({total:.0f}s speech) run={sec:.0f}s")
    tot_ok+=best; tot_all+=total; tot_cov=globals().get('tot_cov',0)+covered
print(f"TOTAL accuracy {100*tot_ok/tot_all:.1f}%  covered {100*tot_cov/tot_all:.1f}%  label-acc-of-covered {100*tot_ok/tot_cov:.1f}%")

if len(sys.argv)>1:
    name=sys.argv[1]
    segs,_=res[name]; gt=json.load(open(f"{BENCH}/{name}.json"))
    spk_gt=sorted({g["speaker"] for g in gt}); spk_pr=sorted({s[0] for s in segs})
    # recompute best mapping
    conf={}
    for g in gt:
        t=g["start"]+0.1
        while t<g["end"]-0.1:
            lab=next((s[0] for s in segs if s[1]<=t<=s[2]),None)
            conf[(g["speaker"],lab)]=conf.get((g["speaker"],lab),0)+0.05; t+=0.05
    best=0; bm=None
    for perm in set(itertools.permutations(spk_pr+[None]*len(spk_gt),len(spk_gt))):
        sc=sum(conf.get((g,p),0) for g,p in zip(spk_gt,perm))
        if sc>best: best,bm=sc,perm
    m=dict(zip(spk_gt,bm)); inv={v:k for k,v in m.items()}
    for g in gt:
        cov={}
        t=g["start"]+0.05
        while t<g["end"]-0.05:
            lab=next((s[0] for s in segs if s[1]<=t<=s[2]),None); cov[lab]=cov.get(lab,0)+0.05; t+=0.05
        ok=cov.get(m[g["speaker"]],0)/max(0.05,sum(cov.values()))
        flag="  " if ok>0.9 else "!!"
        print(f"{flag} {g['start']:5.1f}-{g['end']:5.1f} {g['speaker']} ({g['end']-g['start']:.1f}s) ok={ok:.0%} pred={ {inv.get(k,k):round(v,1) for k,v in cov.items()} }  {g['text'][:40]}")

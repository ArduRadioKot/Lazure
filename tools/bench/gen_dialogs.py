import random,subprocess,json,os
random.seed(7)
SHORT=["Да.","Угу.","Согласен.","Понятно.","Хорошо.","Конечно.","Нет.","Ага, ясно.","Точно.","Спасибо."]
MID=["Давайте обсудим план на неделю.","Бюджет мы утвердили вчера.","Сроки зависят от поставщика.","Я отправлю отчёт до пятницы.","Нужно позвонить клиенту сегодня.","Встреча начнётся в десять утра.","Договор уже подписан обеими сторонами.","Склад находится на другом конце города.","Мы проверили все цифры дважды.","Давайте перенесём совещание на завтра."]
LONG=["Сначала посмотрим задачи по проекту. Потом обсудим бюджет и сроки поставки. После этого решим, кто отвечает за каждый этап.","Вчера я говорил с поставщиком. Он обещал привезти материалы в среду. Но подтвердить пока не может, потому что ждёт ответа от склада.","Отчёт для руководства почти готов. Осталось добавить таблицу с расходами и выводы. Я закончу это до конца дня и отправлю всем."]
VOICES={"A":1.0,"B":0.80,"C":1.22}   # asetrate factor: B lower, C higher
cache={}
def tts(text,spk):
    k=(text,spk)
    if k in cache: return cache[k]
    fn=f"t{len(cache)}.wav"
    subprocess.run(["say","-v","Milena","-o","tmp.aiff",text],check=True)
    f=VOICES[spk]
    af="aresample=16000" if f==1.0 else f"asetrate=16000*{f},aresample=16000,atempo={1/f:.4f}"
    subprocess.run(["ffmpeg","-loglevel","error","-y","-i","tmp.aiff","-ar","16000","-ac","1","-af",af,fn],check=True)
    d=float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",fn]))
    cache[k]=(fn,d); return cache[k]
def build(name,turns,gaps):
    t=0.0; gt=[]; ins=[]; filt=[]
    for i,((spk,text),gap) in enumerate(zip(turns,gaps)):
        fn,d=tts(text,spk)
        t+=gap
        gt.append({"start":round(t,3),"end":round(t+d,3),"speaker":spk,"text":text})
        ins+=["-i",fn]; filt.append(f"[{i}]adelay={int(t*1000)}|{int(t*1000)}[a{i}]")
        t+=d
    n=len(turns)
    graph=";".join(filt)+";"+"".join(f"[a{i}]" for i in range(n))+f"amix=inputs={n}:normalize=0:duration=longest"
    subprocess.run(["ffmpeg","-loglevel","error","-y"]+ins+["-filter_complex",graph,"-ar","16000","-ac","1",f"{name}.wav"],check=True)
    json.dump(gt,open(f"{name}.json","w"),ensure_ascii=False,indent=1)
    print(name,round(t,1),"s",len(gt),"turns")
def seq(spks,n,pool):
    out=[];last=None
    for _ in range(n):
        s=random.choice([x for x in spks if x!=last]); last=s
        out.append((s,random.choice(pool)))
    return out
# R: rapid 3-speaker exchange with short backchannels, tiny gaps
turns=seq("ABC",26,MID+SHORT+SHORT)
build("rapid3",turns,[random.uniform(0.05,0.30) for _ in turns])
# D: calm 2-speaker dialogue
turns=seq("AB",16,MID)
build("dialog2",turns,[random.uniform(0.3,0.8) for _ in turns])
# L: long monologue turns, 2 speakers
turns=[("A" if i%2==0 else "B",LONG[i%3]) for i in range(6)]
build("long2",turns,[random.uniform(0.6,1.2) for _ in turns])
# M: mixed 3 speakers, medium turns, moderate gaps
turns=seq("ABC",18,MID+LONG[:1]+SHORT)
build("mixed3",turns,[random.uniform(0.1,0.6) for _ in turns])

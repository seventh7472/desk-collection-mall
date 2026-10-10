"""Run separately from acceptance.py: both verify stock snapshots."""
import json, urllib.request, urllib.error, uuid, concurrent.futures
from pathlib import Path
base="http://localhost:18082"
def req(method,path,body=None,key=True):
 headers={"Content-Type":"application/json"}
 if key: headers["X-Internal-Key"]="mall-lab-internal-key-change-me"
 r=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),method=method,headers=headers)
 try:
  with urllib.request.urlopen(r,timeout=30) as s:return s.status,json.loads(s.read())
 except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
assert req("GET","/internal/products/1",key=False)[0]==403
stock=req("GET","/api/products/1")[1]["stock"]; batch=uuid.uuid4().hex
quant=stock//2+1;keys=[batch+"a",batch+"b"]
try:
 with concurrent.futures.ThreadPoolExecutor(2) as pool:
  results=list(pool.map(lambda k:req("POST","/internal/products/reservations",{"reservationKey":k,"items":[{"productId":1,"quantity":quant}]}),keys))
 assert sorted(r[0] for r in results)==[200,409],results
finally:
 for k in keys:req("POST",f"/internal/products/reservations/{k}/release")
assert req("GET","/api/products/1")[1]["stock"]==stock
same=uuid.uuid4().hex
try:
 with concurrent.futures.ThreadPoolExecutor(4) as pool:
  result=list(pool.map(lambda _:req("POST","/internal/products/reservations",{"reservationKey":same,"items":[{"productId":1,"quantity":1}]}),range(4)))
 assert all(r[0]==200 for r in result),result
 assert req("GET","/api/products/1")[1]["stock"]==stock-1
finally: req("POST",f"/internal/products/reservations/{same}/release")
assert req("GET","/api/products/1")[1]["stock"]==stock
with concurrent.futures.ThreadPoolExecutor(40) as pool: codes=list(pool.map(lambda _:req("GET","/api/products",key=False)[0],range(100)))
assert 429 in codes,codes
assert all(c in [200,429] for c in codes),codes
out={"passed":True,"mysqlConcurrentReservation": [r[0] for r in results],"sameKeyReplayRequests":4,"stockRestored":stock,"sentinelRequests":100,"statusCounts":{str(c):codes.count(c) for c in set(codes)},"internalUnauthenticatedStatus":403}
Path(__file__).resolve().parents[1].joinpath("infrastructure-results.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
print(json.dumps(out))

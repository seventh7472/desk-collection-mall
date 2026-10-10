"""Real gateway acceptance test. Run after docker compose up."""
import json, time, uuid, urllib.request, urllib.error
from decimal import Decimal
from pathlib import Path
BASE="http://localhost:18080"
checks=[]
def call(method,path,body=None,token=None,status=200):
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=25) as res: code=res.status; raw=res.read().decode()
    except urllib.error.HTTPError as e: code=e.code;raw=e.read().decode()
    result=json.loads(raw) if raw else None
    assert code==status, (method,path,code,status,result)
    checks.append({"method":method,"path":path,"status":code,"passed":True})
    print(method,path,code,flush=True)
    return result

def run():
    assert call("GET","/actuator/health")["status"]=="UP"
    products=call("GET","/api/products")
    assert len(products)>=6
    product=products[0];pid=product["id"];stock=product["stock"]
    assert call("GET","/api/products?q=nonexistent-product-98765")==[]
    username="test_"+uuid.uuid4().hex[:12]
    body={"username":username,"password":"MallTest123!"}
    call("POST","/api/auth/register",body,status=201)
    call("POST","/api/auth/register",body,status=409)
    call("POST","/api/auth/login",{**body,"password":"wrong-password"},status=401)
    token=call("POST","/api/auth/login",body)["token"]
    call("GET","/api/auth/me",token=token)
    call("GET","/api/cart",status=401)
    call("PUT",f"/api/cart/items/{pid}",{"quantity":0},token,status=400)
    call("PUT",f"/api/cart/items/{pid}",{"quantity":2},token)
    cart=call("GET","/api/cart",token=token)
    assert Decimal(str(cart["total"]))==Decimal(str(product["price"]))*2
    rid=uuid.uuid4().hex
    order=call("POST","/api/orders",{"requestId":rid},token,status=201)
    same=call("POST","/api/orders",{"requestId":rid},token,status=201)
    assert same["id"]==order["id"]
    assert call("GET",f"/api/products/{pid}")["stock"]==stock-2
    assert call("GET","/api/cart",token=token)["items"]==[]
    other={"username":"other_"+uuid.uuid4().hex[:12],"password":"MallTest123!"}
    call("POST","/api/auth/register",other,status=201)
    other_token=call("POST","/api/auth/login",other)["token"]
    call("POST",f"/api/orders/{order['id']}/pay",token=other_token,status=404)
    paid=call("POST",f"/api/orders/{order['id']}/pay",token=token)
    assert paid["status"]=="PAID"
    assert call("POST",f"/api/orders/{order['id']}/pay",token=token)["status"]=="PAID"
    call("POST",f"/api/orders/{order['id']}/cancel",token=token,status=409)
    call("PUT",f"/api/cart/items/{pid}",{"quantity":1},token)
    cancel=call("POST","/api/orders",{"requestId":uuid.uuid4().hex},token,status=201)
    assert call("POST",f"/api/orders/{cancel['id']}/cancel",token=token)["status"]=="CANCELLED"
    assert call("POST",f"/api/orders/{cancel['id']}/cancel",token=token)["status"]=="CANCELLED"
    assert call("GET",f"/api/products/{pid}")["stock"]==stock-2
    reply=call("POST","/api/assistant/chat",{"message":"如何支付"})
    assert "reply" in reply and reply["reply"]
    for p in ["/internal/products/1","/internal/users/me"]:
        call("GET",p,status=404)
    return {"passed":True,"checks":checks,"username":username,"orders":[order["id"],cancel["id"]]}

if __name__=="__main__":
    result=run()
    out=Path(__file__).resolve().parents[1]/"acceptance-results.json"
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"PASS: {len(checks)} API requests and business assertions")

"""Provision isolated recording accounts, then build their data through business APIs.

No application code or existing farm records are changed. Bootstrap requires the
operator's existing SSH access; the DB password stays on the server. Resume state
and API evidence go under ignored logs/recording-demo. --action plan is read-only.
"""
from __future__ import annotations

import argparse
from datetime import date, timedelta
import hashlib
import json
import os
from pathlib import Path
import shlex
import sys
import time
import uuid

import bcrypt
import paramiko
import requests

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "logs" / "recording-demo"
OWNER, STAFF = 606001, 606002
FARM, POULTRY, AUDIT_FARM = 60601, 60602, 60603
PHONES = {OWNER: "19900006001", STAFF: "19900006002"}
NAMES = {OWNER: "测试场主", STAFF: "测试员工"}
FARMS = {FARM: "第6组测试养殖场", POULTRY: "第6组测试鸡场", AUDIT_FARM: "第6组审核演示场"}


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def lit(value):
    if value is None:
        return "NULL"
    if isinstance(value, (float, int)):
        return str(value)
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


REMOTE_SQL = r'''
import json,os,pathlib,subprocess,sys
v={}
for raw in pathlib.Path('/etc/zhimu/env').read_text().splitlines():
 line=raw.strip().removeprefix('export ')
 if not line or line.startswith('#') or '=' not in line:continue
 k,x=line.split('=',1);v[k.strip()]=x.strip().strip('"\'')
sql=sys.stdin.buffer.read()
cmd=['mysql','--protocol=TCP','--host='+v.get('DB_HOST','127.0.0.1'),
 '--port='+v.get('DB_PORT','3306'),'--user='+v.get('DB_USER','zhimu'),
 '--database=zhimu','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4']
r=subprocess.run(cmd,input=sql,capture_output=True,env=dict(os.environ,MYSQL_PWD=v['DB_PASSWORD']))
sys.stdout.buffer.write(r.stdout)
sys.stderr.buffer.write(r.stderr)
sys.exit(r.returncode)
'''


class Database:
    def __init__(self, args):
        self.client = paramiko.SSHClient()
        self.client.load_system_host_keys()
        self.client.set_missing_host_key_policy(paramiko.RejectPolicy())
        password = os.environ.get("ZMT_DEMO_SSH_PASSWORD") or os.environ.get("ZMT_AUDIT_SSH_PASSWORD")
        self.client.connect(args.host, username=args.ssh_user, password=password,
                            key_filename=args.ssh_key, look_for_keys=False, allow_agent=False,
                            timeout=15, auth_timeout=15, banner_timeout=15)

    def sql(self, query):
        inp, out, err = self.client.exec_command("python3 -c " + shlex.quote(REMOTE_SQL), timeout=45)
        inp.write(query)
        inp.channel.shutdown_write()
        text = out.read().decode("utf-8")
        error = err.read().decode("utf-8", "replace")
        if out.channel.recv_exit_status():
            raise RuntimeError(error[-2500:])
        return text.strip()

    def close(self):
        self.client.close()


class API:
    def __init__(self, base, phone, password, farm=FARM, client="APP_ANDROID"):
        self.base, self.farm = base.rstrip("/"), farm
        self.s = requests.Session()
        self.s.headers.update({"X-Client": client, "X-App-Version": "1.0.4"})
        data = self.call("POST", "/auth/login/password", {"phone": phone, "password": password})
        self.s.headers["Authorization"] = "Bearer " + data["access_token"]
        self.s.headers["X-Farm-Id"] = str(farm)

    def switch(self, farm):
        self.farm = farm
        self.s.headers["X-Farm-Id"] = str(farm)

    def call(self, method, path, body=None, key=None, timeout=55):
        headers = {"Idempotency-Key": key or str(uuid.uuid4())} if method != "GET" else {}
        r = self.s.request(method, self.base + "/api/v1" + path, json=body, headers=headers, timeout=timeout)
        try:
            payload = r.json()
        except ValueError:
            raise RuntimeError(f"{method} {path}: HTTP {r.status_code}, non-JSON response")
        if payload.get("code") != 0:
            raise RuntimeError(f"{method} {path}: {json.dumps(payload, ensure_ascii=False)}")
        return payload.get("data")


class Provisioner:
    def __init__(self, args):
        self.args = args
        self.path = OUT / "state.json"
        self.state = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {"base_url": args.base_url, "steps": {}}
        if self.state["base_url"] != args.base_url:
            raise RuntimeError("State belongs to a different server")
        ping = requests.get(args.base_url + "/api/v1/ping", timeout=15).json()
        self.today = date.fromisoformat(ping["data"]["time"][:10])
        self.state.setdefault("business_date", self.today.isoformat())
        self.today = date.fromisoformat(self.state["business_date"])
        self.credentials_path = OUT / "credentials.json"

    def d(self, offset=0):
        return (self.today + timedelta(days=offset)).isoformat()

    def memo(self, name, api, method, path, body=None, timeout=55):
        step = f"{api.farm}:{name}"
        if step in self.state["steps"]:
            return self.state["steps"][step]
        key = "recording6-" + hashlib.sha256(step.encode()).hexdigest()[:32]
        value = api.call(method, path, body, key, timeout=timeout)
        self.state["steps"][step] = value
        save(self.path, self.state)
        print("prepared", step, flush=True)
        return value

    def bootstrap(self):
        if self.credentials_path.exists():
            credentials = json.loads(self.credentials_path.read_text(encoding="utf-8"))
        else:
            password = "Zmt6!" + uuid.uuid4().hex[:10]
            credentials = {"password": password, "owner_phone": PHONES[OWNER], "staff_phone": PHONES[STAFF]}
            save(self.credentials_path, credentials)
        hashed = bcrypt.hashpw(credentials["password"].encode(), bcrypt.gensalt()).decode()
        db = Database(self.args)
        try:
            rows = db.sql(f"SELECT id,phone,name FROM t_user WHERE id IN ({OWNER},{STAFF}) OR phone IN ({lit(PHONES[OWNER])},{lit(PHONES[STAFF])});")
            for row in rows.splitlines():
                uid, phone, name = row.split("\t")
                if int(uid) not in PHONES or PHONES[int(uid)] != phone or NAMES[int(uid)] != name:
                    raise RuntimeError("Chosen demo identity is already used; no changes made")
            farms = db.sql(f"SELECT id,name,owner_user_id FROM t_farm WHERE id IN ({FARM},{POULTRY},{AUDIT_FARM});")
            for row in farms.splitlines():
                fid, name, owner = row.split("\t")
                if FARMS.get(int(fid)) != name or int(owner) != OWNER:
                    raise RuntimeError("Chosen demo farm ID is already used; no changes made")
            save(OUT / "bootstrap-before.json", {"users": rows, "farms": farms})
            agreement = requests.get(self.args.base_url + "/api/v1/agreements/current", timeout=15).json()["data"]
            av = agreement.get("agreement_version", "2026-10-26")
            sql = ["START TRANSACTION;"]
            for uid in (OWNER, STAFF):
                sql.append(f"INSERT INTO t_user (id,phone,password_hash,name,status,register_client,agreement_version,agreed_at,password_updated_at,last_farm_id,created_by,created_at,updated_at) SELECT {uid},{lit(PHONES[uid])},{lit(hashed)},{lit(NAMES[uid])},'NORMAL','APP_ANDROID',{lit(av)},NOW(),NOW(),{FARM},{uid},NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE id={uid});")
            for fid, category, scale in ((FARM,101,1000),(POULTRY,201,5000),(AUDIT_FARM,101,500)):
                status = "PENDING" if fid == AUDIT_FARM else "APPROVED"
                sql.append(f"INSERT INTO t_farm (id,name,owner_user_id,main_category_id,scale,contact_name,contact_phone,intro,address,province,city,district,township,adcode,longitude,latitude,audit_status,approved_at,created_by,updated_by,created_at,updated_at) SELECT {fid},{lit(FARMS[fid])},{OWNER},{category},{scale},{lit(NAMES[OWNER])},{lit(PHONES[OWNER])},{lit('第6组视频录制专用测试数据，非真实养殖业务')},{lit('赤峰市松山区太平地镇（演示位置）')},{lit('内蒙古自治区')},{lit('赤峰市')},{lit('松山区')},{lit('太平地镇')},'150404',118.8516,42.3025,'{status}',{'NULL' if status=='PENDING' else 'NOW()'},{OWNER},{OWNER},DATE_SUB(NOW(),INTERVAL 150 DAY),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_farm WHERE id={fid});")
                for uid, role in ((OWNER,"OWNER"),(STAFF,"STAFF")):
                    if fid == AUDIT_FARM and uid == STAFF:
                        continue
                    sql.append(f"INSERT INTO t_farm_member (farm_id,user_id,role,status,active_flag,join_type,joined_at,created_by,updated_by,created_at,updated_at) SELECT {fid},{uid},'{role}','ACTIVE',1,'{'CREATE' if uid==OWNER else 'INVITE'}',NOW(),{OWNER},{OWNER},NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_farm_member WHERE farm_id={fid} AND user_id={uid} AND active_flag=1);")
            snap = {"name":FARMS[AUDIT_FARM],"mainCategoryId":101,"scale":500,"contactName":NAMES[OWNER],"contactPhone":PHONES[OWNER],"address":"赤峰市松山区太平地镇（演示位置）","province":"内蒙古自治区","city":"赤峰市","district":"松山区","township":"太平地镇","adcode":"150404","longitude":118.8516,"latitude":42.3025,"qualificationFileIds":[]}
            sql.append(f"INSERT INTO t_farm_audit (farm_id,submit_no,status,pending_flag,submitted_at,snapshot_json,change_summary,request_id,created_by,created_at,updated_at) SELECT {AUDIT_FARM},1,'PENDING',1,NOW(),{lit(json.dumps(snap,ensure_ascii=False))},{lit('第6组视频录制审核样例')},'recording6-farm-audit',{OWNER},NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_farm_audit WHERE request_id='recording6-farm-audit');")
            sql.append(f"UPDATE t_farm SET latest_audit_id=(SELECT id FROM t_farm_audit WHERE request_id='recording6-farm-audit') WHERE id={AUDIT_FARM} AND latest_audit_id IS NULL;")
            sql.append("COMMIT;")
            (OUT / "bootstrap.sql").write_text("\n".join(sql), encoding="utf-8")
            db.sql("\n".join(sql))
            self.state["bootstrapped"] = True
            save(self.path, self.state)
            print("Created or verified two demo users and their isolated farms; passwords were not printed.")
        finally:
            db.close()

    def login(self, uid=OWNER, client="APP_ANDROID"):
        if not self.state.get("bootstrapped"):
            raise RuntimeError("Bootstrap has not succeeded on the target server")
        c = json.loads(self.credentials_path.read_text(encoding="utf-8"))
        return API(self.args.base_url, PHONES[uid], c["password"], client=client)

    def seed(self):
        api = self.login()
        m = lambda name, method, path, body=None: self.memo(name, api, method, path, body)
        pens = {}
        for key, name, typ, cat, cap in [("A01","1号演示育肥舍","FATTENING",101,300),("A02","2号备用育肥舍","FATTENING",101,200),("A03","3号出栏演示舍","FATTENING",101,200),("B01","4号繁育母猪舍","SOW",102,30)]:
            data = m("pen-"+key,"POST","/pens",dict(pen_code=key,pen_name=name,pen_type=typ,category_id=cat,capacity=cap))
            pens[key] = identity(data,"pen_id")
        members = api.call("GET",f"/farms/{FARM}/members")
        staff = next(x for x in rows(members) if x.get("user_id")==STAFF)
        m("staff-pens","PUT",f"/farms/{FARM}/members/{identity(staff,'member_id')}/pens",dict(pen_ids=[pens['A01'],pens['A02'],pens['B01']],version=staff.get('version',0)))
        batches={}
        for key,count,age,weight,days in [('A01',120,60,32,35),('A03',80,100,65,45)]:
            data=m('batch-'+key,'POST','/batches',dict(pen_id=pens[key],breed_id=1101,source='PURCHASED',entry_date=self.d(-days),entry_age_days=age,entry_count=count,entry_avg_weight=weight,intro_unit_price=650,capacity_confirmed=False))
            batches[key]=identity(data,'batch_id')
        main=batches['A01'];sale=batches['A03']
        feed=m('feed-complete','POST','/feeds',dict(name='演示育肥全价料',feed_type_code='COMPLETE',spec='40kg/袋',pack_unit='袋',pack_size=40,category_id=101,min_stock=1500,warn_days=7))
        feed_id=identity(feed,'feed_id')
        m('feed-inbound','POST','/feed-inbounds',dict(feed_id=feed_id,input_qty=3000,input_unit='MEASURE',unit_price=2.8,inbound_date=self.d(-14),production_date=self.d(-30),shelf_life_date=self.d(150),supplier_name='第6组演示饲料供应商',sync_finance=True))
        low=m('feed-low','POST','/feeds',dict(name='演示保育料（低库存）',feed_type_code='NURSERY',pack_unit='袋',pack_size=40,category_id=101,min_stock=300,warn_days=7))
        m('feed-low-inbound','POST','/feed-inbounds',dict(feed_id=identity(low,'feed_id'),input_qty=120,input_unit='MEASURE',unit_price=3.2,inbound_date=self.d(-7),supplier_name='第6组演示饲料供应商',sync_finance=True))
        plan_version=api.call('GET',f'/batches/{main}/feed-plan').get('version')
        m('feed-plan','PUT',f'/batches/{main}/feed-plan',dict(abnormal_threshold=0.1,stages=[dict(stage_name='演示育肥阶段',day_age_start=1,day_age_end=None,feed_id=feed_id,daily_qty_per_head=2.4,meals_per_day=3,meal_times=['07:00','12:00','18:00'])],version=plan_version))
        m('generate-tasks','POST','/feed-tasks/generate-today')
        for days,weight in [(28,38),(21,44),(14,50),(7,55),(1,60)]:
            m(f'weigh-main-{days}','POST',f'/batches/{main}/weigh-records',dict(weigh_date=self.d(-days),mode='SAMPLE',sample_count=20,total_weight=weight*20))
        m('weigh-sale','POST',f'/batches/{sale}/weigh-records',dict(weigh_date=self.d(-2),mode='SAMPLE',sample_count=20,total_weight=115*20))
        drugs={}
        for key,catalog,category,unit,pack,quantity,price,expires in [('vaccine',7001,'VACCINE','DOSE',10,1000,1.2,180),('drug',7204,'VET_DRUG','ML',100,2000,0.3,120),('disinfect',7302,'DISINFECTANT','L',5,50,12,365),('expiry',7002,'VACCINE','DOSE',10,50,2,10)]:
            data=m('drug-'+key,'POST','/drugs',dict(category=category,catalog_id=catalog,unit=unit,pack_unit='瓶' if key!='disinfect' else '桶',pack_size=pack,min_stock=100 if key!='disinfect' else 10,min_stock_unit=unit))
            did=identity(data,'drug_id');drugs[key]=did
            m('drug-inbound-'+key,'POST','/drug-inbounds',dict(drug_id=did,lot_no='DEMO6-'+key.upper(),inbound_date=self.d(-15),expire_date=self.d(expires),input_qty=quantity,input_unit='MEASURE',unit_price=price,sync_finance=True))
        # Details supply the exact lot IDs; all inventory deductions use normal APIs.
        lot_ids={}
        for key,did in drugs.items():
            detail=api.call('GET',f'/drug-lots/options?drug_id={did}&purpose=USE')
            save(OUT/f'drug-{key}.json',detail)
            lots=detail.get('selectable') or []
            if isinstance(lots,dict):lots=rows(lots)
            if not lots:
                raise RuntimeError(f'Prepared inventory has no usable lot: {key}')
            lot_ids[key]=identity(lots[0],'lot_id','drug_lot_id')
        for days in [0,7]:
            m(f'immune-plan-{days}','POST',f'/batches/{main}/immune-plans',dict(drug_id=drugs['vaccine'],plan_mode='BY_DATE',plan_date=self.d(days),dose=1,method_code='IM',immune_round='演示免疫',due_time='18:00'))
        if 'drug' in lot_ids:
            m('medication','POST','/medication-records',dict(target_type='BATCH',batch_id=main,pen_id=pens['A01'],drug_id=drugs['drug'],drug_lot_id=lot_ids['drug'],dose=0.25,used_qty=20,course_days=3,first_date=self.d(-2),usage_note='视频演示模拟记录',reason='测试数据：异常个体治疗记录演示',executor_id=STAFF))
        m('disinfect-plan','POST','/disinfect-plans',dict(pen_ids=[pens['A01'],pens['A02']],cycle_days=3,start_date=self.d(),end_date=self.d(30),catalog_id=7302,concentration='1:200',method_code='SPRAY',due_time='18:00'))
        if 'disinfect' in lot_ids:
            m('disinfect-record','POST','/disinfect-records',dict(record_type='EMERGENCY',pen_id=pens['A01'],actual_time=self.d(-1)+' 16:00:00',executor_id=STAFF,drug_id=drugs['disinfect'],drug_lot_id=lot_ids['disinfect'],usage_qty=0.5,concentration='1:200',concentration_confirmed=True,method_code='SPRAY',file_ids=[]))
        for key,days in [('DUE',111),('BORN',120)]:
            animal=m('animal-'+key,'POST','/breeding-animals',dict(ear_tag='DEMO6-'+key,breed_id=1201,birth_date=self.d(-550),source='PURCHASED',pen_id=pens['B01'],initial_parity=1))
            aid=identity(animal,'animal_id');detail=api.call('GET',f'/breeding-animals/{aid}')
            m('mating-'+key,'POST','/matings',dict(animal_id=aid,animal_version=detail.get('version',0),mating_date=self.d(-days),mating_method='ARTIFICIAL',boar_info='演示种公猪06'))
            detail=api.call('GET',f'/breeding-animals/{aid}')
            m('pregnancy-'+key,'POST','/pregnancy-checks',dict(animal_id=aid,animal_version=detail.get('version',1),cycle_id=detail.get('current_cycle',{}).get('cycle_id'),check_date=self.d(-days+28),result='POSITIVE',check_method='ULTRASOUND'))
            if key=='BORN':
                detail=api.call('GET',f'/breeding-animals/{aid}')
                m('farrowing','POST','/farrowings',dict(animal_id=aid,animal_version=detail.get('version',2),farrow_date=self.d(-6),total_born=12,live_born=11,stillborn=1,transfer_to_batch=False))
        for days,category,amount,note in [(12,103,680,'演示水电费'),(10,104,1800,'演示人工费'),(5,106,320,'演示设备维修费')]:
            m('expense-'+str(category),'POST','/finance-records',dict(record_type='EXPENSE',amount=amount,category_id=category,record_date=self.d(-days),batch_id=sale,file_ids=[]))
        order=m('sale-order','POST','/sale-orders',dict(batch_id=sale,sale_date=self.d(-1),quantity=30,pen_quantities=[dict(pen_id=pens['A03'],quantity=30)],total_weight=3450,pricing_method='BY_WEIGHT',unit_price=17.2,buyer_name='第6组演示收购商',due_date=self.d(7)))
        oid=identity(order,'sale_order_id','order_id')
        m('sale-receipt','POST',f'/sale-orders/{oid}/receipts',dict(amount=30000,receipt_date=self.d(-1),payment_method='BANK_TRANSFER'))
        self.state['main']={'farm_id':FARM,'pens':pens,'batches':batches,'feed_id':feed_id,'drugs':drugs,'lots':lot_ids,'sale_order_id':oid}
        save(self.path,self.state)
        print('Core business dataset prepared; continue with verification and supplemental scenarios.')

    def supplement(self):
        api=self.login(); employee=self.login(STAFF)
        main=self.state['main'];pens=main['pens'];batch=main['batches']['A01']
        m=lambda name,method,path,body=None:self.memo(name,api,method,path,body)
        m('transfer-demo','POST','/batch-transfers',dict(batch_id=batch,from_pen_id=pens['A01'],to_pen_id=pens['A02'],quantity=10,event_date=self.d(-2),remark='第6组演示转栏记录',capacity_confirmed=False,seen_stock_count=120))
        m('death-demo','POST','/batch-deaths',dict(batch_id=batch,pen_id=pens['A02'],quantity=1,reason_code='SICK',event_date=self.d(-1),executor_user_id=STAFF,remark='第6组模拟死淘记录，非真实事件',file_ids=[],seen_stock_count=10))
        tasks=rows(employee.call('GET','/feed-tasks/today'))
        breakfast=next(x for x in tasks if x['batch_id']==batch and x['meal_no']==1)
        self.memo('breakfast-demo',employee,'POST','/feed-records',dict(task_id=breakfast['task_id'],feed_id=main['feed_id'],actual_qty=breakfast['plan_qty'],executor_id=STAFF,remark='第6组演示早餐；午餐和晚餐留给现场录制'))
        # Yesterday's inspection is history; today's main-pen inspection stays open.
        context=api.call('GET',f"/inspections/form-context?pen_id={pens['A01']}")
        answers=[dict(item_id=x['item_id'],option_id=next(o['option_id'] for o in x['options'] if not o['is_abnormal'])) for x in context['template']['items']]
        m('inspection-yesterday','POST','/inspections/backfills',dict(pen_id=pens['A01'],inspect_date=self.d(-1),executor_id=STAFF,client_request_id='recording6-inspection-history',item_results=answers,is_manual_marked=False,remark='第6组演示历史巡检',location=dict(latitude=42.3025,longitude=118.8516,accuracy_m=20,client_status='OK'),media_file_ids=[]))
        immune=m('immune-history-plan','POST',f'/batches/{batch}/immune-plans',dict(drug_id=main['drugs']['vaccine'],plan_mode='BY_DATE',plan_date=self.d(),dose=1,method_code='IM',immune_round='演示已完成免疫',due_time='18:00'))
        m('immune-history-record','POST','/immune-records',dict(plan_id=identity(immune,'plan_id'),drug_id=main['drugs']['vaccine'],drug_lot_id=main['lots']['vaccine'],dose=1,quantity=119,method_code='IM',vaccinate_date=self.d(),executor_id=STAFF))
        upload_key=f'{FARM}:abnormal-demo-image'
        if upload_key not in self.state['steps']:
            source=ROOT/'lib_media/src/debug/assets/mock/media/upload_sample.jpg'
            with source.open('rb') as f:
                r=employee.s.post(employee.base+'/api/v1/files',data={'scene':'ABNORMAL'},files={'file':('演示附件.jpg',f,'image/jpeg')},headers={'Idempotency-Key':'recording6-abnormal-image'},timeout=45).json()
            if r.get('code')!=0:raise RuntimeError('Demo image upload failed: '+str(r))
            self.state['steps'][upload_key]=r['data'];save(self.path,self.state)
        file_id=identity(self.state['steps'][upload_key],'file_id')
        report=self.memo('abnormal-demo',employee,'POST','/abnormal-reports',dict(client_request_id='recording6-abnormal-case',pen_id=pens['A01'],batch_id=batch,sick_count=3,symptom_ids=[401,403],description='【教学演示病例，非真实疫情】同批3头育肥猪自昨日出现稀软粪便、采食下降，精神略差，未见明显发热，饮水正常。最近有换料。附件为上传功能演示示意图，不能用于外观诊断，请依据文字症状给出鉴别方向、紧急程度和需补充检查。',media_file_ids=[file_id]))
        self.state['main']['report_id']=identity(report,'report_id')
        # A fully sold batch makes settled profit visible in the reports.
        settled_pen=identity(m('pen-settled','POST','/pens',dict(pen_code='A04',pen_name='5号结算演示舍',pen_type='FATTENING',category_id=101,capacity=100)),'pen_id')
        settled=m('batch-settled','POST','/batches',dict(pen_id=settled_pen,breed_id=1101,source='PURCHASED',entry_date=self.d(-50),entry_age_days=95,entry_count=20,entry_avg_weight=60,intro_unit_price=620,capacity_confirmed=False))
        sid=identity(settled,'batch_id')
        v=api.call('GET',f'/batches/{sid}/feed-plan').get('version')
        m('settled-feed-plan','PUT',f'/batches/{sid}/feed-plan',dict(abnormal_threshold=0.1,stages=[dict(stage_name='出栏前演示饲喂',day_age_start=1,feed_id=main['feed_id'],daily_qty_per_head=2.4,meals_per_day=2,meal_times=['07:00','18:00'])],version=v))
        m('generate-settled-tasks','POST','/feed-tasks/generate-today')
        st=next(x for x in rows(api.call('GET','/feed-tasks/today')) if x['batch_id']==sid and x['meal_no']==1)
        m('settled-feed-cost','POST','/feed-records',dict(task_id=st['task_id'],feed_id=main['feed_id'],actual_qty=st['plan_qty'],executor_id=OWNER,remark='第6组结算批次投喂演示'))
        m('settled-utility-cost','POST','/finance-records',dict(record_type='EXPENSE',amount=200,category_id=103,record_date=self.d(-5),batch_id=sid,file_ids=[]))
        complete=m('sale-settled','POST','/sale-orders',dict(batch_id=sid,sale_date=self.d(),quantity=20,pen_quantities=[dict(pen_id=settled_pen,quantity=20)],total_weight=2300,pricing_method='BY_WEIGHT',unit_price=17.2,buyer_name='第6组已结清演示收购商',due_date=self.d()))
        complete_id=identity(complete,'sale_order_id','order_id')
        m('receipt-settled','POST',f'/sale-orders/{complete_id}/receipts',dict(amount=39560,receipt_date=self.d(),payment_method='BANK_TRANSFER'))
        self.state['main']['settled_batch_id']=sid
        save(self.path,self.state)
        # Poultry is separate so the recording can demonstrate incubation without
        # mixing pig and poultry management data into one farm.
        api.switch(POULTRY)
        pp={}
        for key,name,typ in [('J01','1号演示种鸡舍','BREEDER'),('J02','2号演示育雏舍','BROODING')]:
            pp[key]=identity(m('pen-'+key,'POST','/pens',dict(pen_code=key,pen_name=name,pen_type=typ,category_id=201,capacity=3000)),'pen_id')
        staff=next(x for x in rows(api.call('GET',f'/farms/{POULTRY}/members')) if x['user_id']==STAFF)
        m('staff-pens','PUT',f"/farms/{POULTRY}/members/{staff['member_id']}/pens",dict(pen_ids=list(pp.values()),version=staff['version']))
        breeder=identity(m('poultry-breeding-batch','POST','/batches',dict(pen_id=pp['J01'],breed_id=2101,purpose='BREEDING',source='PURCHASED',entry_date=self.d(-90),entry_age_days=160,entry_count=300,entry_avg_weight=2.5,intro_unit_price=35,capacity_confirmed=False)),'batch_id')
        for name,days in [('pending',18),('hatched',25)]:
            inc=m('incubation-'+name,'POST','/incubations',dict(breeding_batch_id=breeder,set_date=self.d(-days),set_count=1000))
            iid=identity(inc,'incubation_id','record_id')
            detail=api.call('GET',f'/incubations/{iid}')
            m('candling-'+name,'POST',f'/incubations/{iid}/candling',dict(candle_date=self.d(-days+7),fertile_count=950,version=detail.get('version',1)))
            if name=='hatched':
                detail=api.call('GET',f'/incubations/{iid}')
                m('hatch-demo','POST',f'/incubations/{iid}/hatch',dict(hatch_date=self.d(-4),hatched_count=920,healthy_count=900,transfer_to_batch=True,target_pen_id=pp['J02'],offspring_breed_id=2101,capacity_confirmed=False,version=detail.get('version',2)))
        self.state['poultry']={'farm_id':POULTRY,'pens':pp,'breeder_batch_id':breeder}
        save(self.path,self.state)
        print('Supplemental scenarios prepared.')

    def assets(self):
        from PIL import Image, ImageDraw, ImageFont
        api=self.login();api.switch(AUDIT_FARM)
        image_path=OUT/'审核演示材料.png'
        if not image_path.exists():
            image=Image.new('RGB',(1200,850),'#f5f8f6');draw=ImageDraw.Draw(image)
            font='C:/Windows/Fonts/msyh.ttc'
            for y,text,size in [(90,'第6组 · 智牧通',34),(185,'养殖场审核演示材料',58),(305,FARMS[AUDIT_FARM],44),(420,'联系人：测试场主',32),(480,'用途：课堂视频录制与功能验收',32),(600,'模拟材料 · 非真实证照 · 不具有证明效力',34)]:
                draw.text((90,y),text,font=ImageFont.truetype(font,size),fill='#214738')
            image.save(image_path)
        step=f'{AUDIT_FARM}:qualification-image'
        if step not in self.state['steps']:
            with image_path.open('rb') as f:
                r=api.s.post(api.base+'/api/v1/files',data={'scene':'FARM_QUAL'},files={'file':('审核演示材料.png',f,'image/png')},headers={'Idempotency-Key':'recording6-qualification-image'},timeout=45).json()
            if r.get('code')!=0:raise RuntimeError('Qualification upload failed: '+str(r))
            self.state['steps'][step]=r['data'];save(self.path,self.state)
        audit=api.call('GET',f'/farms/{AUDIT_FARM}/audit')
        save(OUT/'audit-before-material.json',audit)
        request=dict(name=FARMS[AUDIT_FARM],main_category_id=101,scale=500,contact_name=NAMES[OWNER],contact_phone=PHONES[OWNER],address='赤峰市松山区太平地镇（演示位置）',province='内蒙古自治区',city='赤峰市',district='松山区',township='太平地镇',adcode='150404',longitude=118.8516,latitude=42.3025,qualification_file_ids=[identity(self.state['steps'][step],'file_id')],audit_version=audit.get('audit_version',0))
        self.memo('qualification-registration',api,'PUT',f'/farms/{AUDIT_FARM}/registration',request)
        print('Pending farm audit has a clearly labelled demonstration document.')

    def notice(self):
        db=Database(self.args)
        try:
            title='第6组演示通知：今日养殖工作安排'
            content='<p>本通知仅发送给第6组两个测试账号。</p><p>请按今日待办完成投喂、巡检及消毒登记；异常情况通过异常上报提交。</p><p>全部业务数据仅用于视频录制。</p>'
            sql=['START TRANSACTION;',f"INSERT INTO t_notice (title,level,content,content_length,scope_type,send_mode,status,est_account_count,est_farm_count,dispatch_status,delivered_count,sent_at,created_by,created_at,updated_at) SELECT {lit(title)},'NORMAL',{lit(content)},85,'FARM','NOW','SENT',2,1,'DONE',2,NOW(),0,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_notice WHERE title={lit(title)} AND created_by=0);"]
            sql.append(f"SET @recording_notice=(SELECT id FROM t_notice WHERE title={lit(title)} AND created_by=0 ORDER BY id LIMIT 1);")
            sql.append(f"INSERT INTO t_notice_target (notice_id,target_type,target_value,created_at) SELECT @recording_notice,'FARM','{FARM}',NOW() WHERE NOT EXISTS (SELECT 1 FROM t_notice_target WHERE notice_id=@recording_notice AND target_type='FARM' AND target_value='{FARM}');")
            for uid,role in [(OWNER,'OWNER'),(STAFF,'STAFF')]:
                sql.append(f"INSERT INTO t_message (user_id,farm_id,receiver_role,category,msg_type,level,title,summary,biz_type,biz_id,route,extra_json,merge_key,sort_time,created_by,created_at,updated_at) SELECT {uid},{FARM},'{role}','NOTICE','NOTICE','NORMAL',{lit(title)},{lit('请查看今日工作安排，此通知仅用于第6组演示。')},'NOTICE',@recording_notice,'T07',JSON_OBJECT('notice_id',@recording_notice,'notice_level','NORMAL'),'RECORDING6:NOTICE',NOW(),0,NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_message WHERE user_id={uid} AND merge_key='RECORDING6:NOTICE');")
                sql.append(f"INSERT INTO t_notice_recipient (notice_id,user_id,role_snapshot,farm_id,message_id,created_at,updated_at) SELECT @recording_notice,{uid},'{role}',{FARM},(SELECT id FROM t_message WHERE user_id={uid} AND merge_key='RECORDING6:NOTICE'),NOW(),NOW() WHERE NOT EXISTS (SELECT 1 FROM t_notice_recipient WHERE notice_id=@recording_notice AND user_id={uid});")
            sql+=['COMMIT;','SELECT @recording_notice;']
            nid=int(db.sql('\n'.join(sql)).splitlines()[-1])
            self.state['notice_id']=nid;save(self.path,self.state)
            print('Prepared in-app notice for exactly two demo accounts; no SMS or push was dispatched.')
        finally:db.close()

    def intelligence(self):
        api=self.login();main=self.state['main']
        for uid,title,question in [(OWNER,'录制演示：日常养殖管理','这是教学演示。请结合本场批次信息，列出育肥猪每日巡检需要关注的五个方面，以及发现采食下降时应记录哪些信息。'),(STAFF,'录制演示：员工巡检','这是教学演示。作为饲养员，完成投喂和巡检后应该登记哪些信息？请用简短清单回答。')]:
            actor=api if uid==OWNER else self.login(STAFF)
            session=self.memo('ai-session-'+str(uid),actor,'POST','/ai/sessions',dict(title=title))
            sid=identity(session,'session_id')
            step=f'{FARM}:ai-answer-{uid}'
            if step not in self.state['steps']:
                with actor.s.post(actor.base+'/api/v1/ai/messages',json=dict(session_id=sid,question_text=question,image_file_ids=[]),headers={'Idempotency-Key':'recording6-ai-'+str(uid)},stream=True,timeout=(10,180)) as r:
                    r.raise_for_status(); body='\n'.join(line.decode('utf-8') for line in r.iter_lines() if line)
                (OUT/f'ai-stream-{uid}.txt').write_text(body,encoding='utf-8')
                messages=actor.call('GET',f'/ai/sessions/{sid}/messages')
                self.state['steps'][step]=messages;save(self.path,self.state)
                print('AI conversation prepared for',uid,flush=True)
        advice=self.memo('sale-advice',api,'POST','/sale-advices',dict(batch_id=main['batches']['A03']))
        self.state['sale_advice']=advice
        summary=self.memo('report-ai-summary',api,'POST','/business-reports/ai-summary/regenerate',dict(report_type='TO_DATE'))
        self.state['report_ai_summary']=summary
        save(self.path,self.state)
        print('Requested sale advice and report analysis; verify final AI states separately.')

    def settlements(self):
        """Prepare demo settlement snapshots; the deployed app lacks the end-event snapshot hook."""
        api=self.login();main=self.state['main']
        ids=[main['batches']['A01'],main['batches']['A03'],main['settled_batch_id']]
        for bid in ids:
            self.memo('settlement-detail-'+str(bid),api,'GET',f'/batch-settlements/{bid}')
        if not self.state.get('settlement_snapshots_prepared'):
            db=Database(self.args)
            try:
                scoped=','.join(str(int(x)) for x in ids)
                before=db.sql(f'SELECT * FROM t_batch_settlement WHERE farm_id={FARM} AND batch_id IN ({scoped});')
                save(OUT/'settlement-before.json',{'rows':before})
                # Retain API-calculated feed, medicine, expense and sale totals. Fill
                # purchase cost and the ended batch snapshot from their source batch.
                db.sql(f"""START TRANSACTION;
UPDATE t_batch_settlement s JOIN t_batch b ON b.id=s.batch_id AND b.farm_id=s.farm_id
SET s.intro_count=b.entry_count,s.intro_unit_price=b.intro_unit_price,
 s.cost_intro=ROUND(b.entry_count*COALESCE(b.intro_unit_price,0),2),
 s.status=IF(b.current_count=0 AND b.sold_count=b.entry_count,'LOCKED','RUNNING'),
 s.settled_at=IF(b.current_count=0 AND b.sold_count=b.entry_count,b.ended_at,NULL),
 s.version=s.version+1,s.updated_at=NOW()
WHERE s.farm_id={FARM} AND s.batch_id IN ({scoped});
UPDATE t_batch_settlement s JOIN t_batch b ON b.id=s.batch_id AND b.farm_id=s.farm_id
SET s.cost_total=s.cost_feed+s.cost_intro+s.cost_drug+s.cost_other,
 s.profit=s.sale_income-(s.cost_feed+s.cost_intro+s.cost_drug+s.cost_other),
 s.cost_per_head=ROUND((s.cost_feed+s.cost_intro+s.cost_drug+s.cost_other)/NULLIF(IF(s.status='LOCKED',s.sold_count,b.current_count),0),2),
 s.profit_per_head=ROUND((s.sale_income-s.cost_feed-s.cost_intro-s.cost_drug-s.cost_other)/NULLIF(IF(s.status='LOCKED',s.sold_count,b.current_count),0),2),
 s.calc_at=NOW()
WHERE s.farm_id={FARM} AND s.batch_id IN ({scoped});
COMMIT;""")
                self.state['settlement_snapshots_prepared']=True;save(self.path,self.state)
            finally:db.close()
        results={str(bid):api.call('GET',f'/batch-settlements/{bid}') for bid in ids}
        save(OUT/'settlement-verification.json',results)
        view=api.call('GET','/business-reports/view?report_type=TO_DATE')
        assert view['batch_profits']['items'],'The completed demo settlement must appear in the report'
        summary=self.memo('report-ai-summary-with-settlement',api,'POST','/business-reports/ai-summary/regenerate',dict(report_type='TO_DATE'),timeout=180)
        self.state['report_ai_summary']=summary;save(self.path,self.state)
        print('Settlement snapshots and report analysis prepared.',flush=True)

    def verify(self):
        main=self.state['main'];results=[]
        def check(actor,path,label,expected=0):
            response=actor.s.get(actor.base+'/api/v1'+path,timeout=(10,40))
            value=response.json();ok=value.get('code')==expected
            if expected=='forbidden':ok=response.status_code==403 and value.get('code',0)//100==403
            results.append({'label':label,'path':path,'http':response.status_code,'code':value.get('code'),'ok':ok})
            save(OUT/'verification'/f'{len(results):02d}.json',value)
            print('PASS' if ok else 'FAIL',label,flush=True)
            return value.get('data')
        owner=self.login();batch=main['batches']['A01']
        paths=['/home','/stock/summary','/pens','/batches','/breeding/overview','/breeding-animals','/feed-tasks/today','/feed-stock/overview','/drug-stock/overview','/drug-lots',f'/batches/{batch}/weigh-records','/inspections','/abnormal-reports',f'/batches/{batch}/immune-plans',f'/batches/{batch}/medication-records','/disinfect-plans','/business-overview',f'/finance-records?month={self.d()[:7]}','/sale-orders','/batch-settlements','/business-reports/view?report_type=TO_DATE','/business-reports/ai-summary?report_type=TO_DATE','/market-prices/trend?province_code=150000&category_id=101&breed_id=0&price_type=SLAUGHTER&range_days=30','/messages','/ai/sessions',f"/abnormal-reports/{main['report_id']}/diagnosis-state"]
        for path in paths:check(owner,path,'owner APP '+path)
        owner.switch(POULTRY)
        for path in ['/home','/breeding/overview','/breeding/hatch-due']:
            check(owner,path,'poultry '+path)
        for client in ['APP_ANDROID','WEB']:
            actor=self.login(STAFF,client)
            for path in ['/home','/batches','/feed-tasks/today','/messages','/ai/sessions']:
                check(actor,path,'staff '+client+' '+path)
            check(actor,'/business-overview','staff '+client+' finance denied','forbidden')
            check(actor,f"/batches/{main['batches']['A03']}",'staff '+client+' unassigned batch denied','forbidden')
            actor.switch(1001)
            check(actor,'/home','staff '+client+' unrelated farm denied','forbidden')
        web=self.login(OWNER,'WEB')
        check(web,'/home','owner WEB login and home')
        check(web,'/feed-tasks/today','owner WEB sees same feeding records')
        save(OUT/'verification-summary.json',{'results':results,'passed':sum(r['ok'] for r in results),'total':len(results)})
        if not all(r['ok'] for r in results):raise RuntimeError('Some verification checks failed; inspect evidence')
        print('All',len(results),'API and permission checks passed.')


def rows(value):
    if isinstance(value,list):return value
    if isinstance(value,dict):
        for key in ['list','members','items','records','data']:
            if isinstance(value.get(key),list):return value[key]
    raise ValueError('Unrecognized list response: '+str(value)[:300])


def identity(value,*fields):
    if isinstance(value,int):return value
    for field in (*fields,'id'):
        if isinstance(value,dict) and value.get(field) is not None:return int(value[field])
    raise ValueError('Missing ID: '+str(value)[:500])


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--action',choices=['plan','bootstrap','seed','supplement','assets','notice','intelligence','settlements','verify'],default='plan')
    parser.add_argument('--base-url',default='http://8.208.12.217')
    parser.add_argument('--host',default='8.208.12.217')
    parser.add_argument('--ssh-user',default='root')
    parser.add_argument('--ssh-key')
    args=parser.parse_args()
    if args.action=='plan':
        print(json.dumps({'accounts':[{'phone':PHONES[x],'name':NAMES[x],'role':'OWNER' if x==OWNER else 'STAFF'} for x in (OWNER,STAFF)],'farms':FARMS,'writes':'Only dedicated demo identities, farms, and their business records','status':'Awaiting authenticated server access'},ensure_ascii=False,indent=2))
        return
    provisioner=Provisioner(args)
    getattr(provisioner,args.action)()


if __name__=='__main__':
    main()

#!/bin/bash
# 考务四步端到端验收：报名 → 编排 → 发证 → 查档
BASE=http://localhost:8080/api
CJ=$(dirname "$0")/.e2e-cookies.txt
rm -f $CJ
pass=0; fail=0

jget() { python3 -c "
import sys,json
d=json.load(sys.stdin)
e=sys.argv[1].replace(\"['data']\",\"d['data']\").replace(\"['message']\",\"d['message']\")
print(eval(e))" "$1"; }
jchk() { python3 -c "
import sys,json
d=json.load(sys.stdin)
e=sys.argv[1].replace(\"['data']\",\"d['data']\").replace(\"['message']\",\"d['message']\")
print('YES' if eval(e) else 'NO')" "$1"; }

check() { # desc expected actual
  if [ "$2" == "$3" ]; then echo "PASS: $1 (= $3)"; pass=$((pass+1));
  else echo "FAIL: $1 期望[$2] 实际[$3]"; fail=$((fail+1)); fi
}
req() { # method path [json]
  if [ -n "$3" ]; then
    curl -s -b $CJ -X $1 "$BASE$2" -H 'Content-Type: application/json' -d "$3"
  else
    curl -s -b $CJ -X $1 "$BASE$2"
  fi
}

echo "===== 0) 未登录访问受保护接口：401 可读 ====="
r=$(curl -s -w "|%{http_code}" $BASE/archive/sites)
check "未登录HTTP状态" "401" "${r##*|}"
check "未登录可读消息" "YES" "$(echo "${r%|*}" | jchk "['message'].startswith('未登录')")"

echo; echo "===== 1) 登录 ====="
r=$(curl -s -c $CJ -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"bootstrap","password":"bootstrap"}')
check "登录成功" "YES" "$(echo "$r" | jchk "['success']")"
r=$(curl -s -c /tmp/cj-bad.txt -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"bootstrap","password":"wrong"}' -w "|%{http_code}")
check "错误密码401" "401" "${r##*|}"

echo; echo "===== 2) 建档：考点、考场（容量、位置） ====="
r=$(req POST /archive/sites '{"siteCode":"SITE01","siteName":"第一考点","location":"市府路1号","contactPerson":"刘老师","contactPhone":"0577-11"}')
check "建考点" "SITE01" "$(echo "$r" | jget "['data']['siteCode']")"
r=$(req POST /archive/sites '{"siteCode":"SITE01","siteName":"重","location":"x"}' -w "|%{http_code}" 2>/dev/null); r=$(curl -s -b $CJ -X POST $BASE/archive/sites -H 'Content-Type: application/json' -d '{"siteCode":"SITE01","siteName":"重","location":"x"}' -w "|%{http_code}")
check "考点编码重复409" "409" "${r##*|}"
check "冲突可读消息" "YES" "$(echo "${r%|*}" | jchk "'考点编码已存在' in ['message']")"
SID=$(req GET /archive/sites | jget "['data'][0]['id']")
r=$(req POST /archive/sites/$SID/rooms '{"roomCode":"101","roomName":"第1考场","floorNo":"1F","location":"A楼101","capacity":30}')
check "建考场1(30座)" "30" "$(echo "$r" | jget "['data']['capacity']")"
r=$(req POST /archive/sites/$SID/rooms '{"roomCode":"102","roomName":"第2考场","floorNo":"1F","location":"A楼102","capacity":10}')
check "建考场2(10座)" "10" "$(echo "$r" | jget "['data']['capacity']")"
r=$(curl -s -b $CJ -X POST $BASE/archive/sites/$SID/rooms -H 'Content-Type: application/json' -d '{"roomCode":"103","roomName":"第3考场","location":"A楼103","capacity":0}' -w "|%{http_code}")
check "容量0被拒400" "400" "${r##*|}"

echo; echo "===== 3) 报名：名册先预览影响范围，再导入（逐行可读） ====="
ROSTER='{"batchNo":"RO202609A","subject":"语文","rows":[
 {"candidateNo":"C001","name":"张三","idNo":"ID001","phone":"13800000001"},
 {"candidateNo":"C002","name":"李四","idNo":"ID002","phone":"13800000002"},
 {"candidateNo":"C003","name":"王五","idNo":"ID003","phone":"13800000003"},
 {"candidateNo":"C004","name":"","idNo":"ID004"},
 {"candidateNo":"C005","name":"张三","idNo":"ID001"}]}'
r=$(req POST /registration/roster/preview "$ROSTER")
check "预览总行数" "5" "$(echo "$r" | jget "['data']['totalRows']")"
check "预览接收3行" "3" "$(echo "$r" | jget "['data']['acceptedCount']")"
check "预览拒收2行" "2" "$(echo "$r" | jget "['data']['rejectedCount']")"
check "预览不落库(dryRun)" "True" "$(echo "$r" | jget "['data']['dryRun']")"
check "拒收原因可读" "YES" "$(echo "$r" | jchk "any('不能为空' in (x['reason'] or '') for x in ['data']['lines'])")"
check "批内证件号重复可读" "YES" "$(echo "$r" | jchk "any('证件号重复' in (x['reason'] or '') for x in ['data']['lines'])")"
r=$(req POST /registration/roster/import "$ROSTER")
check "执行实际挂入3条" "3" "$(echo "$r" | jget "['data']['insertedRows']")"
r=$(curl -s -b $CJ -X POST $BASE/registration/roster/import -H 'Content-Type: application/json' -d "$ROSTER" -w "|%{http_code}")
check "同批次重复导入409" "409" "${r##*|}"

ROSTER2='{"batchNo":"RO202609B","subject":"语文","rows":[
 {"candidateNo":"C006","name":"赵六","idNo":"ID006"},
 {"candidateNo":"C007","name":"孙七","idNo":"ID007"}]}'
req POST /registration/roster/import "$ROSTER2" >/dev/null

echo; echo "===== 4) 勘误：不抹旧值，留更正痕迹 ====="
WID=$(req GET /registration/roster/RO202609A | jget "['data'][2]['id']")
r=$(req POST /registration/examinees/$WID/corrections '{"fieldName":"phone","newValue":"13911112222","reason":"本人申报更正"}')
check "勘误返回旧值" "13800000003" "$(echo "$r" | jget "['data']['oldValue']")"
check "勘误记录新值" "13911112222" "$(echo "$r" | jget "['data']['newValue']")"
check "勘误经办人" "bootstrap" "$(echo "$r" | jget "['data']['correctedName']")"
r=$(req GET /registration/examinees/$WID)
check "当前值已更新" "13911112222" "$(echo "$r" | jget "['data']['phone']")"
r=$(curl -s -b $CJ -X POST $BASE/registration/examinees/$WID/corrections -H 'Content-Type: application/json' -d '{"fieldName":"phone","newValue":"13911112222"}' -w "|%{http_code}")
check "新旧相同400" "400" "${r##*|}"

echo; echo "===== 5) 编排：预览→生成，一人一条，证号/考场/座位 ====="
r=$(req POST /arrange/batches/preview '{"rosterBatchNo":"RO202609A","subject":"语文"}')
B1=$(echo "$r" | jget "['data']['batchNo']")
check "首批可执行" "YES" "$(echo "$r" | jchk "['data']['readyToGenerate']")"
check "首批影响3人" "3" "$(echo "$r" | jget "['data']['candidateCount']")"
check "首批影响考场数" "1" "$(echo "$r" | jget "['data']['roomCount']")"
P0=$(echo "$r" | jget "['data']['candidates'][0]")
check "第1人座位号" "1" "$(echo "$r" | jget "['data']['candidates'][0]['seatNo']")"
check "第1人证号" "2026000001" "$(echo "$r" | jget "['data']['candidates'][0]['ticketNo']")"
check "第1人考场" "101" "$(echo "$r" | jget "['data']['candidates'][0]['roomCode']")"
r=$(req POST /arrange/batches/$B1/generate)
check "生成成功状态" "GENERATED" "$(echo "$r" | jget "['data']['arrangeStatus']")"
T1=$(echo "$r" | jget "['data']['candidates'][0]['ticketNo']")
r=$(curl -s -b $CJ -X POST $BASE/arrange/batches/$B1/generate -w "|%{http_code}")
check "重复执行409" "409" "${r##*|}"

echo; echo "===== 6) 证号撞号直接拒收 ====="
r=$(req POST /arrange/batches/preview '{"rosterBatchNo":"RO202609B","subject":"语文","ticketNos":{"C006":"'$T1'","C007":"X999"}}')
check "撞号批次不可执行" "NO" "$(echo "$r" | jchk "['data']['readyToGenerate']")"
check "阻塞项含撞号" "YES" "$(echo "$r" | jchk "any('撞号' in b for b in ['data']['blockers'])")"
BDUP=$(echo "$r" | jget "['data']['batchNo']")
r=$(curl -s -b $CJ -X POST $BASE/arrange/batches/$BDUP/generate -w "|%{http_code}")
check "撞号执行拒收409" "409" "${r##*|}"
check "拒收消息可读" "YES" "$(echo "${r%|*}" | jchk "'证号' in ['message']")"
r=$(req GET /query/all-tickets)
check "撞号未产生准考证(仍3张)" "3" "$(echo "$r" | jget "len(['data'])")"

echo; echo "===== 7) 回退可追溯（状态回退留痕、证号不回收） ====="
r=$(req POST /arrange/batches/preview '{"rosterBatchNo":"RO202609B","subject":"语文"}')
B2=$(echo "$r" | jget "['data']['batchNo']")
OLD_NOS=$(echo "$r" | jget "[c['ticketNo'] for c in ['data']['candidates']]")
req POST /arrange/batches/$B2/generate >/dev/null
r=$(req POST /arrange/batches/$B2/rollback '{"reason":"考场调整"}')
check "回退后状态" "ROLLED_BACK" "$(echo "$r" | jget "['data']['arrangeStatus']")"
check "回退原因留痕" "考场调整" "$(echo "$r" | jget "['data']['rollbackReason']")"
check "回退经办人" "bootstrap" "$(echo "$r" | jget "['data']['rollbackName']")"
r=$(req GET /arrange/batches/$B2/logs)
check "批次日志3条(预览/生成/回退)" "3" "$(echo "$r" | jget "len(['data'])")"
check "日志动作序列" "['PREVIEW', 'GENERATE', 'ROLLBACK']" "$(echo "$r" | jget "[x['action'] for x in ['data']]")"
r=$(req GET /registration/examinees/$(req GET /registration/roster/RO202609B | jget "['data'][0]['id']"))
check "回退后考生恢复已报名" "REGISTERED" "$(echo "$r" | jget "['data']['status']")"
r=$(req POST /arrange/batches/preview '{"rosterBatchNo":"RO202609B","subject":"语文"}')
B3=$(echo "$r" | jget "['data']['batchNo']")
NEW_NOS=$(echo "$r" | jget "[c['ticketNo'] for c in ['data']['candidates']]")
if [ "$OLD_NOS" != "$NEW_NOS" ]; then RN=YES; else RN=NO; fi
check "回退证号不复用" "YES" "$RN"
req POST /arrange/batches/$B3/generate >/dev/null

echo; echo "===== 8) 发证：打证、发放各留经办钟点；批量先预览 ====="
r=$(req POST /issue/print/preview '{"arrangeBatchNo":"'$B1'"}')
check "打证预览影响3张" "3" "$(echo "$r" | jget "['data']['willProcessCount']")"
check "打证预览不执行" "0" "$(echo "$r" | jget "['data']['processedCount']")"
r=$(req POST /issue/print/execute '{"arrangeBatchNo":"'$B1'"}')
check "打证执行3张" "3" "$(echo "$r" | jget "['data']['processedCount']")"
r=$(req POST /issue/distribute/preview '{"arrangeBatchNo":"'$B1'"}')
check "发放预览影响3张" "3" "$(echo "$r" | jget "['data']['willProcessCount']")"
r=$(req POST /issue/distribute/execute '{"arrangeBatchNo":"'$B1'","remark":"班主任代领"}')
check "发放执行3张" "3" "$(echo "$r" | jget "['data']['processedCount']")"
r=$(req POST /issue/print/execute '{"arrangeBatchNo":"'$B1'"}')
check "已发证再打证全跳过" "3" "$(echo "$r" | jget "len(['data']['skipped'])")"
r=$(req GET /issue/tickets/$T1/logs)
check "单张轨迹2条" "2" "$(echo "$r" | jget "len(['data'])")"
check "轨迹动作" "['PRINT', 'ISSUE']" "$(echo "$r" | jget "[x['action'] for x in ['data']]")"
check "轨迹有经办" "bootstrap" "$(echo "$r" | jget "['data'][0]['operatorName']")"
check "轨迹有钟点" "YES" "$(echo "$r" | jchk "['data'][0]['operateTime'] is not None")"

echo; echo "===== 9) 查档：按证号、按姓名 ====="
r=$(req GET /query/tickets/$T1)
check "证号查档姓名" "张三" "$(echo "$r" | jget "['data']['ticket']['name']")"
check "档案含发证轨迹2条" "2" "$(echo "$r" | jget "len(['data']['issueLogs'])")"
WANG_TICKET=$(curl -s -b $CJ -G "$BASE/query/tickets" --data-urlencode "name=王五" | jget "['data'][0]['ticketNo']")
r=$(req GET /query/tickets/$WANG_TICKET)
check "王五档案含勘误1条" "1" "$(echo "$r" | jget "len(['data']['corrections'])")"
check "档案勘误旧值保留" "13800000003" "$(echo "$r" | jget "['data']['corrections'][0]['oldValue']")"
check "档案勘误有经办人" "bootstrap" "$(echo "$r" | jget "['data']['corrections'][0]['correctedName']")"
r=$(curl -s -b $CJ -G "$BASE/query/tickets" --data-urlencode "name=张三")
check "姓名查到准考证" "1" "$(echo "$r" | jget "len(['data'])")"
r=$(curl -s -b $CJ -G "$BASE/query/examinees" --data-urlencode "name=王")
check "姓名查名册(模糊)" "王五" "$(echo "$r" | jget "['data'][0]['name']")"
r=$(req GET /query/tickets/NOTEXIST -w "|%{http_code}")
r=$(curl -s -b $CJ "$BASE/query/tickets/NOTEXIST" -w "|%{http_code}")
check "不存在证号404" "404" "${r##*|}"
check "404可读" "YES" "$(echo "${r%|*}" | jchk "'未查到' in ['message']")"

echo; echo "===== 10) 版本可追溯 ====="
r=$(req GET /system/versions)
check "版本数" "2" "$(echo "$r" | jget "len(['data'])")"
check "当前业务版本" "exam-1" "$(echo "$r" | jget "['data'][1]['versionNo']")"

echo; echo "================ 结果：PASS=$pass FAIL=$fail ================"
[ $fail -eq 0 ]

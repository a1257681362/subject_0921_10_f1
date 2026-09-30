package com.evops.arrange.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.archiving.entity.ExamRoom;
import com.evops.archiving.entity.ExamSite;
import com.evops.archiving.mapper.ExamRoomMapper;
import com.evops.archiving.mapper.ExamSiteMapper;
import com.evops.arrange.dto.ArrangePreview;
import com.evops.arrange.dto.ArrangeRequest;
import com.evops.arrange.dto.ArrangeSnapshot;
import com.evops.arrange.dto.PlannedCandidate;
import com.evops.arrange.dto.PlannedRoom;
import com.evops.arrange.entity.AdmissionTicket;
import com.evops.arrange.entity.ArrangeBatch;
import com.evops.arrange.entity.BatchOpLog;
import com.evops.arrange.mapper.AdmissionTicketMapper;
import com.evops.arrange.mapper.ArrangeBatchMapper;
import com.evops.arrange.mapper.BatchOpLogMapper;
import com.evops.common.BusinessException;
import com.evops.common.Operator;
import com.evops.common.OperatorContext;
import com.evops.registration.entity.Examinee;
import com.evops.registration.mapper.ExamineeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 编排步。
 * 阶段结果（预览快照、批次日志）与最终结论（准考证）分开保存：
 * 预览只建批次与快照；执行才落准考证；回退删准考证但批次/快照/日志全部保留。
 */
@Service
public class ArrangeService {

    private static final Logger log = LoggerFactory.getLogger(ArrangeService.class);
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int TICKET_NO_LENGTH = 10;

    private final ArrangeBatchMapper batchMapper;
    private final AdmissionTicketMapper ticketMapper;
    private final BatchOpLogMapper opLogMapper;
    private final ExamineeMapper examineeMapper;
    private final ExamRoomMapper roomMapper;
    private final ExamSiteMapper siteMapper;
    private final ObjectMapper objectMapper;

    public ArrangeService(ArrangeBatchMapper batchMapper, AdmissionTicketMapper ticketMapper,
                          BatchOpLogMapper opLogMapper, ExamineeMapper examineeMapper,
                          ExamRoomMapper roomMapper, ExamSiteMapper siteMapper,
                          ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.ticketMapper = ticketMapper;
        this.opLogMapper = opLogMapper;
        this.examineeMapper = examineeMapper;
        this.roomMapper = roomMapper;
        this.siteMapper = siteMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 1) 预览：展示影响范围，存阶段结果（快照），不落准考证
    // ------------------------------------------------------------------

    @Transactional
    public ArrangePreview preview(ArrangeRequest request) {
        List<Examinee> candidates = loadCandidates(request);
        List<ExamRoom> rooms = loadActiveRooms();
        Map<Long, ExamSite> siteMap = loadSites(rooms);
        Map<Long, Set<Integer>> occupiedSeats = loadOccupiedSeats();

        List<String> blockers = new ArrayList<>();
        if (candidates.isEmpty()) {
            blockers.add("没有符合条件的待编排考生（仅状态为已报名且未出证者可编排）");
        }

        // 外部准考证号校验：键必须都能对上、批内不重复、与历史证号不撞
        Map<String, String> externalNos = normalizeExternalNos(request.getTicketNos());
        Set<String> externalValues = new HashSet<>();
        Set<String> candidateNoSet = candidates.stream().map(Examinee::getCandidateNo).collect(Collectors.toSet());
        for (Map.Entry<String, String> entry : externalNos.entrySet()) {
            String ticketNo = entry.getValue();
            if (ticketNo.length() > 40) {
                blockers.add("准考证号超长（>40）：" + ticketNo);
            }
            if (!externalValues.add(ticketNo)) {
                blockers.add("提交清单内证号重复：" + ticketNo);
            }
            if (!candidateNoSet.contains(entry.getKey())) {
                blockers.add("证号清单中的报名号不在本次待编排范围：" + entry.getKey());
            }
            Long exists = ticketMapper.selectCount(new QueryWrapper<AdmissionTicket>()
                    .eq("ticket_no", ticketNo));
            if (exists != null && exists > 0) {
                blockers.add("证号已被占用，撞号拒收：" + ticketNo);
            }
        }

        int totalCapacity = rooms.stream().mapToInt(ExamRoom::getCapacity).sum();
        int occupiedCount = occupiedSeats.values().stream().mapToInt(Set::size).sum();
        int availableCapacity = totalCapacity - occupiedCount;
        int shortage = Math.max(0, candidates.size() - availableCapacity);
        if (shortage > 0) {
            blockers.add("可用座位不足：考生 " + candidates.size() + " 人，可用座位 "
                    + availableCapacity + "（总容量 " + totalCapacity + "，已占 "
                    + occupiedCount + "），缺口 " + shortage + " 座");
        }

        // 计算座位方案（容量够时）
        List<PlannedRoom> plannedRooms = new ArrayList<>();
        List<PlannedCandidate> plannedCandidates = new ArrayList<>();
        if (shortage == 0 && !candidates.isEmpty()) {
            assignSeats(candidates, rooms, siteMap, occupiedSeats, externalNos,
                    request.getTicketNoPrefix(), plannedRooms, plannedCandidates);
        }

        ArrangeSnapshot snapshot = new ArrangeSnapshot();
        snapshot.setRosterBatchNo(request.getRosterBatchNo());
        snapshot.setSubject(request.getSubject());
        snapshot.setTicketNoPrefix(request.getTicketNoPrefix());
        snapshot.setCandidateCount(candidates.size());
        snapshot.setRoomCount(plannedRooms.size());
        snapshot.setTotalCapacity(totalCapacity);
        snapshot.setExternalOnly(!externalNos.isEmpty());
        snapshot.setBlockers(blockers);
        snapshot.setRooms(plannedRooms);
        snapshot.setCandidates(plannedCandidates);

        Operator operator = OperatorContext.current();
        ArrangeBatch batch = new ArrangeBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setSubject(request.getSubject());
        batch.setTotalCount(candidates.size());
        batch.setRoomCount(plannedRooms.size());
        batch.setSeatStrategy("SEQUENTIAL");
        batch.setTicketNoPrefix(request.getTicketNoPrefix());
        batch.setArrangeStatus("PREVIEWED");
        batch.setPreviewSnapshot(toJson(snapshot));
        batch.setPreviewBy(operator.getId());
        batch.setPreviewName(operator.getName());
        batch.setPreviewTime(LocalDateTime.now());
        batchMapper.insert(batch);

        writeOpLog(batch, "PREVIEW", operator,
                "预览影响范围：考生 " + candidates.size() + " 人，考场 " + plannedRooms.size()
                        + " 个，阻塞项 " + blockers.size() + " 条"
                        + (blockers.isEmpty() ? "" : "：" + String.join("；", blockers)));

        return toPreview(batch, snapshot, blockers);
    }

    // ------------------------------------------------------------------
    // 2) 执行：按快照落准考证，撞号整批拒收（事务回滚，不留半条）
    // ------------------------------------------------------------------

    @Transactional
    public ArrangePreview generate(String batchNo) {
        ArrangeBatch batch = getBatchByNo(batchNo);
        if (!"PREVIEWED".equals(batch.getArrangeStatus())) {
            throw BusinessException.conflict(
                    "批次当前状态为 " + batch.getArrangeStatus() + "，只有预览态批次可执行编排");
        }
        ArrangeSnapshot snapshot = fromJson(batch.getPreviewSnapshot());
        if (snapshot.getBlockers() != null && !snapshot.getBlockers().isEmpty()) {
            // 预览存在阻塞项（撞号、座位不足等），执行端直接拒收，不产生任何准考证
            throw BusinessException.conflict(
                    "编排方案不可执行，整批已拒收：" + String.join("；", snapshot.getBlockers()));
        }
        if (snapshot.getCandidates() == null || snapshot.getCandidates().isEmpty()) {
            throw BusinessException.conflict(
                    "该预览批次无可生成的编排方案（无待编排考生），请核对后重新预览");
        }

        // 漂移检查：预览后若名册/考场有变，要求重新预览，不拿旧方案硬落
        List<String> drift = detectDrift(snapshot);
        if (!drift.isEmpty()) {
            throw BusinessException.conflict("预览方案已过期，请重新预览后再执行：" + String.join("；", drift));
        }

        Operator operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        Set<Long> siteIds = snapshot.getCandidates().stream()
                .map(PlannedCandidate::getSiteId).collect(Collectors.toSet());
        Map<Long, String> siteLocations = siteIds.isEmpty()
                ? Collections.emptyMap()
                : siteMapper.selectList(new QueryWrapper<ExamSite>().in("id", siteIds))
                    .stream().collect(Collectors.toMap(ExamSite::getId, ExamSite::getLocation));
        List<AdmissionTicket> tickets = new ArrayList<>();
        for (PlannedCandidate planned : snapshot.getCandidates()) {
            AdmissionTicket ticket = new AdmissionTicket();
            ticket.setTicketNo(planned.getTicketNo());
            ticket.setArrangeBatchId(batch.getId());
            ticket.setArrangeBatchNo(batch.getBatchNo());
            ticket.setExamineeId(planned.getExamineeId());
            ticket.setCandidateNo(planned.getCandidateNo());
            ticket.setName(planned.getName());
            ticket.setIdType(planned.getIdType());
            ticket.setIdNo(planned.getIdNo());
            ticket.setSubject(planned.getSubject());
            ticket.setSiteId(planned.getSiteId());
            ticket.setSiteCode(planned.getSiteCode());
            ticket.setSiteName(planned.getSiteName());
            ticket.setSiteLocation(siteLocations.get(planned.getSiteId()));
            ticket.setRoomId(planned.getRoomId());
            ticket.setRoomCode(planned.getRoomCode());
            ticket.setRoomLocation(planned.getRoomLocation());
            ticket.setSeatNo(planned.getSeatNo());
            ticket.setTicketStatus("GENERATED");
            ticket.setGeneratedBy(operator.getId());
            ticket.setGeneratedName(operator.getName());
            ticket.setGeneratedTime(now);
            tickets.add(ticket);
        }

        try {
            for (AdmissionTicket ticket : tickets) {
                ticketMapper.insert(ticket);
            }
        } catch (DuplicateKeyException ex) {
            // 证号撞号直接拒收：整批回滚，不产生一人多条或半截数据
            log.warn("编排批次 {} 证号撞号，整批拒收：{}", batchNo, ex.getMessage());
            throw BusinessException.conflict("证号撞号，整批已拒收，未生成任何准考证；请核对证号后重新预览");
        }

        for (PlannedCandidate planned : snapshot.getCandidates()) {
            Examinee examinee = examineeMapper.selectById(planned.getExamineeId());
            examinee.setStatus("ASSIGNED");
            examineeMapper.updateById(examinee);
        }

        batch.setArrangeStatus("GENERATED");
        batch.setGeneratedBy(operator.getId());
        batch.setGeneratedName(operator.getName());
        batch.setGeneratedTime(LocalDateTime.now());
        batchMapper.updateById(batch);

        List<String> ticketNos = tickets.stream().map(AdmissionTicket::getTicketNo).collect(Collectors.toList());
        writeOpLog(batch, "GENERATE", operator,
                "生成准考证 " + ticketNos.size() + " 张，证号区间 "
                        + (ticketNos.isEmpty() ? "-" : ticketNos.get(0) + " ~ " + ticketNos.get(ticketNos.size() - 1)));

        return toPreview(batch, snapshot, Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // 3) 回退：撤准考证，批次与所有日志保留（状态回退可追溯）
    // ------------------------------------------------------------------

    @Transactional
    public ArrangeBatch rollback(String batchNo, String reason) {
        ArrangeBatch batch = getBatchByNo(batchNo);
        if (!"GENERATED".equals(batch.getArrangeStatus())) {
            throw BusinessException.conflict(
                    "仅已生成状态的批次可回退，当前状态：" + batch.getArrangeStatus());
        }
        List<AdmissionTicket> tickets = ticketMapper.selectList(new QueryWrapper<AdmissionTicket>()
                .eq("arrange_batch_id", batch.getId()));
        boolean inProgress = tickets.stream().anyMatch(t ->
                "PRINTED".equals(t.getTicketStatus()) || "ISSUED".equals(t.getTicketStatus()));
        if (inProgress) {
            throw BusinessException.conflict("批次内已有准考证进入打证/发放环节，不可回退，请先收回处理");
        }

        for (AdmissionTicket ticket : tickets) {
            Examinee examinee = examineeMapper.selectById(ticket.getExamineeId());
            if (examinee != null) {
                examinee.setStatus("REGISTERED");
                examineeMapper.updateById(examinee);
            }
        }
        ticketMapper.delete(new QueryWrapper<AdmissionTicket>()
                .eq("arrange_batch_id", batch.getId()));

        Operator operator = OperatorContext.current();
        batch.setArrangeStatus("ROLLED_BACK");
        batch.setRollbackBy(operator.getId());
        batch.setRollbackName(operator.getName());
        batch.setRollbackTime(LocalDateTime.now());
        batch.setRollbackReason(reason);
        batchMapper.updateById(batch);

        writeOpLog(batch, "ROLLBACK", operator,
                "回退编排，撤销准考证 " + tickets.size() + " 张（证号不回收复用）。原因："
                        + (reason == null ? "未填写" : reason));
        return batch;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public ArrangeBatch getBatchByNo(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw BusinessException.badRequest("编排批次号不能为空");
        }
        ArrangeBatch batch = batchMapper.selectOne(new QueryWrapper<ArrangeBatch>()
                .eq("batch_no", batchNo.trim()), false);
        if (batch == null) {
            throw BusinessException.notFound("编排批次不存在：" + batchNo);
        }
        return batch;
    }

    public List<ArrangeBatch> listBatches() {
        return batchMapper.selectList(new QueryWrapper<ArrangeBatch>().orderByDesc("id"));
    }

    public List<BatchOpLog> listBatchLogs(String batchNo) {
        ArrangeBatch batch = getBatchByNo(batchNo);
        return opLogMapper.selectList(new QueryWrapper<BatchOpLog>()
                .eq("batch_id", batch.getId()).orderByAsc("id"));
    }

    public ArrangePreview getPreview(String batchNo) {
        ArrangeBatch batch = getBatchByNo(batchNo);
        return toPreview(batch, fromJson(batch.getPreviewSnapshot()), Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private List<Examinee> loadCandidates(ArrangeRequest request) {
        QueryWrapper<Examinee> wrapper = new QueryWrapper<Examinee>()
                .eq("status", "REGISTERED")
                .orderByAsc("batch_no", "line_no");
        if (request.getRosterBatchNo() != null && !request.getRosterBatchNo().trim().isEmpty()) {
            wrapper.eq("batch_no", request.getRosterBatchNo().trim());
        }
        if (request.getSubject() != null && !request.getSubject().trim().isEmpty()) {
            wrapper.eq("subject", request.getSubject().trim());
        }
        return examineeMapper.selectList(wrapper);
    }

    private List<ExamRoom> loadActiveRooms() {
        return roomMapper.selectList(new QueryWrapper<ExamRoom>()
                .eq("status", "ACTIVE")
                .orderByAsc("site_id", "room_code"));
    }

    private Map<Long, ExamSite> loadSites(List<ExamRoom> rooms) {
        if (rooms.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> siteIds = rooms.stream().map(ExamRoom::getSiteId).collect(Collectors.toSet());
        return siteMapper.selectList(new QueryWrapper<ExamSite>().in("id", siteIds))
                .stream().collect(Collectors.toMap(ExamSite::getId, s -> s));
    }

    private void assignSeats(List<Examinee> candidates, List<ExamRoom> rooms,
                             Map<Long, ExamSite> siteMap, Map<Long, Set<Integer>> occupiedSeats,
                             Map<String, String> externalNos, String ticketNoPrefix,
                             List<PlannedRoom> plannedRooms, List<PlannedCandidate> plannedCandidates) {
        // 考场占用视图：从已有准考证占用座位之后排，避免座位撞号
        Map<Long, List<Integer>> roomFreeSeats = new HashMap<>();
        for (ExamRoom room : rooms) {
            Set<Integer> occupied = occupiedSeats.getOrDefault(room.getId(), Collections.emptySet());
            List<Integer> free = new ArrayList<>();
            for (int seat = 1; seat <= room.getCapacity(); seat++) {
                if (!occupied.contains(seat)) {
                    free.add(seat);
                }
            }
            roomFreeSeats.put(room.getId(), free);

            PlannedRoom pr = new PlannedRoom();
            pr.setRoomId(room.getId());
            pr.setSiteId(room.getSiteId());
            ExamSite site = siteMap.get(room.getSiteId());
            if (site != null) {
                pr.setSiteCode(site.getSiteCode());
                pr.setSiteName(site.getSiteName());
            }
            pr.setRoomCode(room.getRoomCode());
            pr.setRoomName(room.getRoomName());
            pr.setRoomLocation(room.getLocation());
            pr.setCapacity(room.getCapacity());
            pr.setAssignedCount(0);
            plannedRooms.add(pr);
        }
        Map<Long, PlannedRoom> plannedRoomMap = plannedRooms.stream()
                .collect(Collectors.toMap(PlannedRoom::getRoomId, p -> p, (a, b) -> a, LinkedHashMap::new));

        // 顺序排座：按考场次序逐个填入空闲座位
        int roomIndex = 0;
        int seatIndex = 0;
        for (Examinee c : candidates) {
            List<Integer> freeSeats = roomFreeSeats.get(rooms.get(roomIndex).getId());
            while (seatIndex >= freeSeats.size()) {
                roomIndex++;
                seatIndex = 0;
                freeSeats = roomFreeSeats.get(rooms.get(roomIndex).getId());
            }
            ExamRoom room = rooms.get(roomIndex);
            int seatNo = freeSeats.get(seatIndex);
            ExamSite site = siteMap.get(room.getSiteId());
            PlannedCandidate pc = new PlannedCandidate();
            pc.setExamineeId(c.getId());
            pc.setCandidateNo(c.getCandidateNo());
            pc.setName(c.getName());
            pc.setIdType(c.getIdType());
            pc.setIdNo(c.getIdNo());
            pc.setSubject(c.getSubject());
            pc.setSiteId(room.getSiteId());
            if (site != null) {
                pc.setSiteCode(site.getSiteCode());
                pc.setSiteName(site.getSiteName());
            }
            pc.setRoomId(room.getId());
            pc.setRoomCode(room.getRoomCode());
            pc.setRoomLocation(room.getLocation());
            pc.setSeatNo(seatNo);

            String external = externalNos.get(c.getCandidateNo());
            if (external != null) {
                pc.setTicketNo(external);
                pc.setExternalTicketNo(true);
            } else {
                pc.setTicketNo(nextAutoTicketNo(ticketNoPrefix));
                pc.setExternalTicketNo(false);
            }
            plannedCandidates.add(pc);

            PlannedRoom pr = plannedRoomMap.get(room.getId());
            pr.setAssignedCount(pr.getAssignedCount() + 1);
            if (pr.getSeatFrom() == null) {
                pr.setSeatFrom(seatNo);
            }
            pr.setSeatTo(seatNo);
            seatIndex++;
        }
        plannedRooms.removeIf(pr -> pr.getAssignedCount() == 0);
    }

    private Map<Long, Set<Integer>> loadOccupiedSeats() {
        Map<Long, Set<Integer>> occupied = new HashMap<>();
        for (AdmissionTicket ticket : ticketMapper.selectList(null)) {
            occupied.computeIfAbsent(ticket.getRoomId(), k -> new HashSet<>()).add(ticket.getSeatNo());
        }
        return occupied;
    }

    private String nextAutoTicketNo(String prefix) {
        Long seq = ticketMapper.nextTicketSequence();
        String p = prefix == null ? "" : prefix.trim();
        return p + String.format("%0" + TICKET_NO_LENGTH + "d", seq);
    }

    private List<String> detectDrift(ArrangeSnapshot snapshot) {
        List<String> drift = new ArrayList<>();
        for (PlannedCandidate planned : snapshot.getCandidates()) {
            Examinee examinee = examineeMapper.selectById(planned.getExamineeId());
            if (examinee == null) {
                drift.add("考生已从名册移除：" + planned.getCandidateNo());
                continue;
            }
            if (!"REGISTERED".equals(examinee.getStatus())) {
                drift.add("考生状态已变更（" + examinee.getStatus() + "）：" + examinee.getName());
            }
            if (planned.isExternalTicketNo()) {
                Long exists = ticketMapper.selectCount(new QueryWrapper<AdmissionTicket>()
                        .eq("ticket_no", planned.getTicketNo()));
                if (exists != null && exists > 0) {
                    drift.add("证号已被占用：" + planned.getTicketNo());
                }
            }
        }
        for (PlannedRoom plannedRoom : snapshot.getRooms()) {
            ExamRoom room = roomMapper.selectById(plannedRoom.getRoomId());
            if (room == null || !"ACTIVE".equals(room.getStatus())) {
                drift.add("考场已不可用：" + plannedRoom.getRoomCode());
            } else if (!room.getCapacity().equals(plannedRoom.getCapacity())) {
                drift.add("考场容量已变更：" + plannedRoom.getRoomCode()
                        + " " + plannedRoom.getCapacity() + "->" + room.getCapacity());
            }
        }
        return drift;
    }

    private Map<String, String> normalizeExternalNos(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null && !k.trim().isEmpty() && !v.trim().isEmpty()) {
                normalized.put(k.trim(), v.trim());
            }
        });
        return normalized;
    }

    private String generateBatchNo() {
        return "AP" + LocalDateTime.now().format(BATCH_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private void writeOpLog(ArrangeBatch batch, String action, Operator operator, String detail) {
        BatchOpLog opLog = new BatchOpLog();
        opLog.setBatchId(batch.getId());
        opLog.setBatchNo(batch.getBatchNo());
        opLog.setAction(action);
        opLog.setDetail(detail);
        opLog.setOperatorId(operator.getId());
        opLog.setOperatorName(operator.getName());
        opLog.setOperateTime(LocalDateTime.now());
        opLogMapper.insert(opLog);
    }

    private ArrangePreview toPreview(ArrangeBatch batch, ArrangeSnapshot snapshot, List<String> blockers) {
        ArrangePreview preview = new ArrangePreview();
        preview.setBatchId(batch.getId());
        preview.setBatchNo(batch.getBatchNo());
        preview.setArrangeStatus(batch.getArrangeStatus());
        preview.setReadyToGenerate(blockers.isEmpty()
                && snapshot.getCandidateCount() > 0
                && "PREVIEWED".equals(batch.getArrangeStatus()));
        preview.setCandidateCount(snapshot.getCandidateCount());
        preview.setRoomCount(snapshot.getRoomCount());
        preview.setTotalCapacity(snapshot.getTotalCapacity());
        preview.setCapacityShortage(Math.max(0, snapshot.getCandidateCount() - snapshot.getTotalCapacity()));
        preview.setBlockers(blockers);
        preview.setRooms(snapshot.getRooms());
        preview.setCandidates(snapshot.getCandidates());
        return preview;
    }

    private String toJson(ArrangeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("编排快照序列化失败", e);
        }
    }

    private ArrangeSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, ArrangeSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("编排快照无法解析", e);
        }
    }
}

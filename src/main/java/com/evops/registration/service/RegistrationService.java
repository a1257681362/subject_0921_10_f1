package com.evops.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.Operator;
import com.evops.common.OperatorContext;
import com.evops.registration.dto.RosterImportRequest;
import com.evops.registration.dto.RosterImportResult;
import com.evops.registration.dto.RowCheckResult;
import com.evops.registration.entity.Examinee;
import com.evops.registration.entity.RosterCorrection;
import com.evops.registration.mapper.ExamineeMapper;
import com.evops.registration.mapper.RosterCorrectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 报名步：名册挂入（先预览影响范围，再执行；逐行给出可读结果）、勘误留痕（不抹旧值）。
 */
@Service
public class RegistrationService {

    /** 允许勘误的字段 */
    private static final Map<String, String> CORRECTABLE_FIELDS;
    static {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "姓名");
        fields.put("gender", "性别");
        fields.put("idType", "证件类型");
        fields.put("idNo", "证件号");
        fields.put("phone", "联系电话");
        fields.put("schoolOrOrg", "学校/单位");
        fields.put("subject", "报考科目");
        fields.put("candidateNo", "报名号");
        CORRECTABLE_FIELDS = Collections.unmodifiableMap(fields);
    }

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ExamineeMapper examineeMapper;
    private final RosterCorrectionMapper correctionMapper;

    public RegistrationService(ExamineeMapper examineeMapper, RosterCorrectionMapper correctionMapper) {
        this.examineeMapper = examineeMapper;
        this.correctionMapper = correctionMapper;
    }

    /** 名册导入：dryRun=true 只展示影响范围；dryRun=false 挂入通过校验的行。 */
    @Transactional
    public RosterImportResult importRoster(RosterImportRequest request, boolean dryRun) {
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw BusinessException.badRequest("名册为空，无可导入的行");
        }

        String batchNo = normalizeBatchNo(request.getBatchNo(), dryRun);
        if (!dryRun && examineeMapper.selectCount(new QueryWrapper<Examinee>()
                .eq("batch_no", batchNo)) > 0) {
            throw BusinessException.conflict("报名批次号已存在：" + batchNo);
        }

        List<RowCheckResult> lineResults = new ArrayList<>();
        Set<String> candidateSeen = new HashSet<>();
        Set<String> idNoSeen = new HashSet<>();

        int lineNo = 0;
        for (RosterImportRequest.Row row : request.getRows()) {
            lineNo++;
            String reason = validateRow(row, candidateSeen, idNoSeen, lineResults);
            if (reason != null) {
                lineResults.add(RowCheckResult.reject(lineNo, row, reason));
            } else {
                lineResults.add(RowCheckResult.accept(lineNo, row));
            }
        }

        RosterImportResult result = new RosterImportResult();
        result.setDryRun(dryRun);
        result.setBatchNo(batchNo);
        result.setSubject(trim(request.getSubject()));
        result.setTotalRows(request.getRows().size());
        result.setAcceptedCount((int) lineResults.stream().filter(RowCheckResult::isAccepted).count());
        result.setRejectedCount(result.getTotalRows() - result.getAcceptedCount());
        result.setLines(lineResults);

        if (!dryRun) {
            String defaultSubject = trim(request.getSubject());
            Operator operator = OperatorContext.current();
            int inserted = 0;
            for (int i = 0; i < lineResults.size(); i++) {
                RowCheckResult check = lineResults.get(i);
                if (!check.isAccepted()) {
                    continue;
                }
                RosterImportRequest.Row row = request.getRows().get(i);
                Examinee examinee = new Examinee();
                examinee.setBatchNo(batchNo);
                examinee.setLineNo(check.getLineNo());
                examinee.setCandidateNo(trim(row.getCandidateNo()));
                examinee.setName(trim(row.getName()));
                examinee.setGender(trim(row.getGender()));
                examinee.setIdType(orDefault(trim(row.getIdType()), "ID_CARD"));
                examinee.setIdNo(trim(row.getIdNo()));
                examinee.setPhone(trim(row.getPhone()));
                examinee.setSchoolOrOrg(trim(row.getSchoolOrOrg()));
                examinee.setSubject(orDefault(trim(row.getSubject()), defaultSubject));
                examinee.setStatus("REGISTERED");
                examineeMapper.insert(examinee);
                inserted++;
            }
            result.setInsertedRows(inserted);
        }
        return result;
    }

    public List<Examinee> listByBatch(String batchNo) {
        return examineeMapper.selectList(new QueryWrapper<Examinee>()
                .eq("batch_no", batchNo).orderByAsc("line_no"));
    }

    public Examinee getExaminee(Long id) {
        Examinee examinee = examineeMapper.selectById(id);
        if (examinee == null) {
            throw BusinessException.notFound("名册记录不存在：id=" + id);
        }
        return examinee;
    }

    /**
     * 名册勘误：旧值保留在 t_roster_correction，当前表更新为新值，更正痕迹可查。
     */
    @Transactional
    public RosterCorrection correct(Long examineeId, String fieldName, String newValue, String reason) {
        Examinee examinee = getExaminee(examineeId);
        String field = fieldName == null ? null : fieldName.trim();
        if (!CORRECTABLE_FIELDS.containsKey(field)) {
            throw BusinessException.badRequest(
                    "不允许勘误的字段：" + fieldName + "；可勘误字段：" + CORRECTABLE_FIELDS.keySet());
        }
        String trimmedValue = newValue == null ? null : newValue.trim();
        if (trimmedValue == null || trimmedValue.isEmpty()) {
            throw BusinessException.badRequest("更正后的新值不能为空");
        }
        if ("idNo".equals(field)) {
            Examinee dup = examineeMapper.selectOne(new QueryWrapper<Examinee>()
                    .eq("id_type", orDefault(examinee.getIdType(), "ID_CARD"))
                    .eq("id_no", trimmedValue)
                    .ne("id", examineeId), false);
            if (dup != null) {
                throw BusinessException.conflict("证件号已被其他考生使用，勘误拒收：" + trimmedValue);
            }
        }

        String oldValue = readField(examinee, field);
        if (trimmedValue.equals(oldValue)) {
            throw BusinessException.badRequest("新值与旧值相同，无需勘误");
        }

        writeField(examinee, field, trimmedValue);
        examineeMapper.updateById(examinee);

        Operator operator = OperatorContext.current();
        RosterCorrection correction = new RosterCorrection();
        correction.setExamineeId(examineeId);
        correction.setFieldName(field);
        correction.setOldValue(oldValue);
        correction.setNewValue(trimmedValue);
        correction.setReason(reason);
        correction.setCorrectedBy(operator.getId());
        correction.setCorrectedName(operator.getName());
        correction.setCorrectedTime(LocalDateTime.now());
        correctionMapper.insert(correction);
        return correction;
    }

    public List<RosterCorrection> listCorrections(Long examineeId) {
        return correctionMapper.selectList(new QueryWrapper<RosterCorrection>()
                .eq("examinee_id", examineeId).orderByDesc("corrected_time"));
    }

    // ------------------------------------------------------------------

    private String validateRow(RosterImportRequest.Row row, Set<String> candidateSeen,
                               Set<String> idNoSeen, List<RowCheckResult> previous) {
        if (row == null) {
            return "空行";
        }
        if (isBlank(row.getCandidateNo())) {
            return "报名号不能为空";
        }
        if (isBlank(row.getName())) {
            return "姓名不能为空";
        }
        if (isBlank(row.getIdNo())) {
            return "证件号不能为空";
        }
        String candidateNo = row.getCandidateNo().trim();
        String idNo = row.getIdNo().trim();
        if (!candidateSeen.add(candidateNo)) {
            return "本批次内报名号重复：" + candidateNo;
        }
        if (!idNoSeen.add(idNo)) {
            return "本批次内证件号重复：" + idNo;
        }
        Examinee existingId = examineeMapper.selectOne(new QueryWrapper<Examinee>()
                .eq("id_type", orDefault(trim(row.getIdType()), "ID_CARD"))
                .eq("id_no", idNo), false);
        if (existingId != null) {
            return "证件号在名册中已存在（考生 " + existingId.getName()
                    + "，批次 " + existingId.getBatchNo() + "），不可重复报名";
        }
        return null;
    }

    private String normalizeBatchNo(String requested, boolean dryRun) {
        String batchNo = trim(requested);
        if (batchNo != null) {
            return batchNo;
        }
        // 预览也给出确定批次号，执行时可原样回传，保证预览/执行是同一份影响范围
        return "RO" + LocalDateTime.now().format(BATCH_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private String readField(Examinee e, String field) {
        switch (field) {
            case "name": return e.getName();
            case "gender": return e.getGender();
            case "idType": return e.getIdType();
            case "idNo": return e.getIdNo();
            case "phone": return e.getPhone();
            case "schoolOrOrg": return e.getSchoolOrOrg();
            case "subject": return e.getSubject();
            case "candidateNo": return e.getCandidateNo();
            default: throw new IllegalStateException(field);
        }
    }

    private void writeField(Examinee e, String field, String value) {
        switch (field) {
            case "name": e.setName(value); break;
            case "gender": e.setGender(value); break;
            case "idType": e.setIdType(value); break;
            case "idNo": e.setIdNo(value); break;
            case "phone": e.setPhone(value); break;
            case "schoolOrOrg": e.setSchoolOrOrg(value); break;
            case "subject": e.setSubject(value); break;
            case "candidateNo": e.setCandidateNo(value); break;
            default: throw new IllegalStateException(field);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String orDefault(String value, String def) {
        return value == null || value.isEmpty() ? def : value;
    }
}

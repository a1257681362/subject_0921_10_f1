package com.evops.archiving.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.archiving.entity.ExamRoom;
import com.evops.archiving.entity.ExamSite;
import com.evops.archiving.mapper.ExamRoomMapper;
import com.evops.archiving.mapper.ExamSiteMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 考点、考场建档：编码查重，容量、位置记全。 */
@Service
public class ArchiveService {

    private final ExamSiteMapper siteMapper;
    private final ExamRoomMapper roomMapper;

    public ArchiveService(ExamSiteMapper siteMapper, ExamRoomMapper roomMapper) {
        this.siteMapper = siteMapper;
        this.roomMapper = roomMapper;
    }

    @Transactional
    public ExamSite createSite(ExamSite request) {
        requireText(request.getSiteCode(), "考点编码");
        requireText(request.getSiteName(), "考点名称");
        requireText(request.getLocation(), "考点位置");
        if (siteMapper.selectCount(new QueryWrapper<ExamSite>()
                .eq("site_code", request.getSiteCode())) > 0) {
            throw BusinessException.conflict("考点编码已存在：" + request.getSiteCode());
        }
        ExamSite site = new ExamSite();
        site.setSiteCode(request.getSiteCode());
        site.setSiteName(request.getSiteName());
        site.setLocation(request.getLocation());
        site.setContactPerson(request.getContactPerson());
        site.setContactPhone(request.getContactPhone());
        site.setRemark(request.getRemark());
        site.setStatus("ACTIVE");
        siteMapper.insert(site);
        return site;
    }

    public List<ExamSite> listSites() {
        return siteMapper.selectList(new QueryWrapper<ExamSite>().orderByAsc("site_code"));
    }

    public ExamSite getSite(Long siteId) {
        ExamSite site = siteMapper.selectById(siteId);
        if (site == null) {
            throw BusinessException.notFound("考点不存在：id=" + siteId);
        }
        return site;
    }

    @Transactional
    public ExamRoom createRoom(Long siteId, ExamRoom request) {
        ExamSite site = getSite(siteId);
        requireText(request.getRoomCode(), "考场号");
        requireText(request.getRoomName(), "考场名称");
        requireText(request.getLocation(), "考场位置");
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw BusinessException.badRequest("考场容量必须为正整数");
        }
        if (roomMapper.selectCount(new QueryWrapper<ExamRoom>()
                .eq("site_id", siteId)
                .eq("room_code", request.getRoomCode())) > 0) {
            throw BusinessException.conflict(
                    "考点内考场号已存在：" + site.getSiteCode() + "/" + request.getRoomCode());
        }
        ExamRoom room = new ExamRoom();
        room.setSiteId(siteId);
        room.setRoomCode(request.getRoomCode());
        room.setRoomName(request.getRoomName());
        room.setFloorNo(request.getFloorNo());
        room.setLocation(request.getLocation());
        room.setCapacity(request.getCapacity());
        room.setStatus("ACTIVE");
        roomMapper.insert(room);
        return room;
    }

    public List<ExamRoom> listRooms(Long siteId) {
        QueryWrapper<ExamRoom> wrapper = new QueryWrapper<ExamRoom>().orderByAsc("site_id", "room_code");
        if (siteId != null) {
            wrapper.eq("site_id", siteId);
        }
        return roomMapper.selectList(wrapper);
    }

    public int totalCapacity() {
        return roomMapper.selectList(new QueryWrapper<ExamRoom>().eq("status", "ACTIVE"))
                .stream().mapToInt(ExamRoom::getCapacity).sum();
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BusinessException.badRequest(field + "不能为空");
        }
    }
}

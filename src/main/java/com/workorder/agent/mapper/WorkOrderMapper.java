package com.workorder.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.workorder.agent.entity.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    /**
     * 按类型统计工单数量
     */
    @Select("SELECT work_type, COUNT(*) as cnt FROM work_order " +
            "WHERE create_time >= #{start} AND create_time <= #{end} " +
            "GROUP BY work_type")
    List<Map<String, Object>> countByType(@Param("start") String start, @Param("end") String end);

    /**
     * 按模块统计工单数量
     */
    @Select("SELECT module, COUNT(*) as cnt FROM work_order " +
            "WHERE create_time >= #{start} AND create_time <= #{end} " +
            "GROUP BY module ORDER BY cnt DESC")
    List<Map<String, Object>> countByModule(@Param("start") String start, @Param("end") String end);

    /**
     * 按优先级统计
     */
    @Select("SELECT priority, COUNT(*) as cnt FROM work_order " +
            "WHERE create_time >= #{start} AND create_time <= #{end} " +
            "GROUP BY priority")
    List<Map<String, Object>> countByPriority(@Param("start") String start, @Param("end") String end);

    /**
     * 查询超时未处理工单
     */
    @Select("SELECT * FROM work_order WHERE status IN (0, 1) " +
            "AND create_time <= #{deadline} " +
            "ORDER BY priority ASC, create_time ASC")
    List<WorkOrder> findTimeoutOrders(@Param("deadline") String deadline);

    /**
     * 查询最近 N 天的工单总量
     */
    @Select("SELECT COUNT(*) FROM work_order WHERE create_time >= #{start}")
    Long countSince(@Param("start") String start);
}

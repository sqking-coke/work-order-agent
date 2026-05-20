package com.workorder.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.*;
import com.workorder.agent.entity.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.*;

@Mapper
public interface WorkOrderReportMapper extends BaseMapper<WorkOrderReport> {

    @Select("SELECT * FROM work_order_report WHERE report_type = #{reportType} " +
            "AND report_period = #{reportPeriod} LIMIT 1")
    WorkOrderReport findByPeriod(@Param("reportType") String reportType,
                                  @Param("reportPeriod") String reportPeriod);

    @Select("SELECT * FROM work_order_report ORDER BY create_time DESC")
    List<WorkOrderReport> listAll();
}

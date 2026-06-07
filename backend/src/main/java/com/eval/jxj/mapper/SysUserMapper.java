package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.dto.response.ClassOptionVO;
import com.eval.jxj.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("select grade, class_name as className, max(college) as college, max(major) as major, count(*) as studentCount "
            + "from sys_user "
            + "where role = 'student' and is_deleted = 0 and class_name is not null and class_name <> '' "
            + "group by grade, class_name "
            + "order by grade, class_name")
    List<ClassOptionVO> selectStudentClassOptions();

    @Select("select count(*) from sys_user u "
            + "join eval_batch b on b.id = #{batchId} and b.is_deleted = 0 "
            + "where u.role = 'student' and u.is_deleted = 0 "
            + "and (b.target_type = 'all' or exists ("
            + "  select 1 from batch_class bc where bc.batch_id = b.id "
            + "    and bc.class_name = u.class_name "
            + "    and (bc.grade = u.grade or (bc.grade is null and u.grade is null)))) ")
    int countStudentsInBatchScope(@Param("batchId") String batchId);
}

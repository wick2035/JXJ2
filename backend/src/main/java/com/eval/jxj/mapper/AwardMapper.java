package com.eval.jxj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.jxj.entity.Award;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AwardMapper extends BaseMapper<Award> {
    @Select("select a.* from award a inner join batch_basic_award bba on bba.award_id = a.id "
            + "where bba.batch_id = #{batchId} and a.is_deleted = 0 order by a.category asc, a.name asc")
    List<Award> selectBatchBasicAwards(@Param("batchId") String batchId);
}

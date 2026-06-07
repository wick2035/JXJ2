package com.eval.jxj.controller;

import com.eval.jxj.common.Result;
import com.eval.jxj.dto.request.AwardCreateRequest;
import com.eval.jxj.dto.response.AwardVO;
import com.eval.jxj.entity.AwardLevelDef;
import com.eval.jxj.entity.BatchAward;
import com.eval.jxj.mapper.BatchAwardMapper;
import com.eval.jxj.service.AwardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AwardController {

    private final AwardService awardService;
    private final BatchAwardMapper batchAwardMapper;

    public AwardController(AwardService awardService, BatchAwardMapper batchAwardMapper) {
        this.awardService = awardService;
        this.batchAwardMapper = batchAwardMapper;
    }

    @GetMapping("/awards")
    public Result<List<AwardVO>> listAwards(@RequestParam(required = false) String category) {
        return Result.ok(awardService.listAwards(category));
    }

    @GetMapping("/awards/{id}")
    public Result<AwardVO> getAward(@PathVariable String id) {
        return Result.ok(awardService.getAward(id));
    }

    @PostMapping("/awards")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AwardVO> createAward(@Valid @RequestBody AwardCreateRequest request) {
        return Result.ok(awardService.createAward(request));
    }

    @PutMapping("/awards/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AwardVO> updateAward(@PathVariable String id, @Valid @RequestBody AwardCreateRequest request) {
        return Result.ok(awardService.updateAward(id, request));
    }

    @DeleteMapping("/awards/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> deleteAward(@PathVariable String id) {
        awardService.deleteAward(id);
        return Result.ok();
    }

    @GetMapping("/award-levels")
    public Result<List<AwardLevelDef>> listLevels() {
        return Result.ok(awardService.listLevels());
    }

    @GetMapping("/batches/{batchId}/awards")
    public Result<List<AwardVO>> listBatchAwards(@PathVariable String batchId,
                                                  @RequestParam(required = false) String category) {
        return Result.ok(awardService.listBatchAwards(batchId, category));
    }

    @PostMapping("/batches/{batchId}/awards")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> saveBatchAwards(@PathVariable String batchId, @RequestBody List<BatchAward> awards) {
        // Delete existing and re-insert
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BatchAward> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(BatchAward::getBatchId, batchId);
        batchAwardMapper.delete(wrapper);

        for (BatchAward ba : awards) {
            ba.setId(null);
            ba.setBatchId(batchId);
            batchAwardMapper.insert(ba);
        }
        return Result.ok();
    }
}

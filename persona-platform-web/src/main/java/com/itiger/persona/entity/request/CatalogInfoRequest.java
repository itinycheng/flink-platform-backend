package com.itiger.persona.entity.request;

import com.baomidou.mybatisplus.annotation.TableName;
import com.itiger.persona.entity.CatalogInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author Shik
 * @Title: CatalogInfoRequest
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/5/13 下午2:09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class CatalogInfoRequest extends CatalogInfo {

    private List<String> catalogIds;

}
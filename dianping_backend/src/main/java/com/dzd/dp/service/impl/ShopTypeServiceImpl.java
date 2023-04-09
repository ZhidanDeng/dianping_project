package com.dzd.dp.service.impl;

import cn.hutool.json.JSONUtil;
import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.ShopType;
import com.dzd.dp.mapper.ShopTypeMapper;
import com.dzd.dp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.dzd.dp.constant.Constant.CACHE_SHOPTYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate template;

    @Override
    public Result queryShopTypeList() {
        Long size = template.opsForList().size(CACHE_SHOPTYPE_LIST);
        if (size == null || size==0){
            //从数据库中查询
            List<ShopType> list = this.query().orderByAsc("sort").list();
            Iterator<ShopType> iterator = list.iterator();
            while (iterator.hasNext()){
                ShopType next = iterator.next();
                String shopTypeStr = JSONUtil.toJsonStr(next);
                template.opsForList().rightPush(CACHE_SHOPTYPE_LIST,shopTypeStr);
            }
            return Result.ok(list);
        }else {
            List<ShopType> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String shopTypeStr = template.opsForList().leftPop(CACHE_SHOPTYPE_LIST);
                ShopType shopType = JSONUtil.toBean(shopTypeStr, ShopType.class);
                list.add(shopType);
            }
            return Result.ok(list);
        }
    }
}

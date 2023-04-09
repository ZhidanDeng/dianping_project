package com.hmdp;

import com.dzd.dp.entity.Shop;
import com.dzd.dp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dzd.dp.constant.Constant.SHOP_GEO_KEY;

@SpringBootTest
class DianPingApplicationTests {

    @Autowired
    private IShopService shopService;
    @Autowired
    private StringRedisTemplate template;

    static final ThreadLocal<String> t = new ThreadLocal<>();

    static void print(String str){
        System.out.println(str+":"+t.get());
        t.remove();
    }

    public static void main(String[] args) {

    }


    @Test
    public void loadShopType() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            String key = SHOP_GEO_KEY+typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for(Shop shop:shops){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            template.opsForGeo().add(key,locations);
        }

    }
}



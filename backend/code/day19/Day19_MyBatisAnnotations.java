package code.day19;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Day 19: MyBatis 注解 + 动态 SQL + 分页。
 *
 * <p>五项练习：
 * <ol>
 *   <li>注解 CRUD（@Select/@Insert/@Update/@Delete）</li>
 *   <li>动态 SQL &lt;script&gt; 条件查询</li>
 *   <li>批量插入 &lt;foreach&gt;</li>
 *   <li>RowBounds 物理分页</li>
 *   <li>注解 vs XML 对比</li>
 * </ol>
 *
 * @author Reboot
 * @since 2026-07-18
 */
public class Day19_MyBatisAnnotations {

    static SqlSessionFactory factory;

    static {
        try {
            factory = new SqlSessionFactoryBuilder()
                    .build(Resources.getResourceAsStream("code/day19/mybatis-config.xml"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        annotationCRUD();
        dynamicSQL();
        batchInsert();
        pagination();
    }

    /** 练习1: 注解 CRUD */
    static void annotationCRUD() {
        System.out.println("========== 1. 注解 CRUD ==========");
        try (SqlSession session = factory.openSession(true)) {
            DeviceMapper mapper = session.getMapper(DeviceMapper.class);

            // SELECT ALL
            List<Device> devices = mapper.findAll();
            System.out.println("所有设备 (" + devices.size() + "):");
            devices.forEach(d -> System.out.println("  " + d));

            // SELECT BY ID
            Device d = mapper.findById(1L);
            System.out.println("\nID=1: " + d);

            // INSERT
            Device newDevice = new Device();
            newDevice.setName("光照传感器-L1");
            newDevice.setType("传感器");
            newDevice.setLocation("三号车间");
            newDevice.setStatus("ONLINE");
            mapper.insert(newDevice);
            System.out.println("\n插入成功, 自增ID=" + newDevice.getId());

            // UPDATE
            mapper.updateStatus(4L, "MAINTENANCE");
            System.out.println("ID=4 状态→MAINTENANCE");

            // DELETE
            mapper.deleteById(7L);
            System.out.println("ID=7 已删除");
        }
    }

    /** 练习2: 动态 SQL */
    static void dynamicSQL() {
        System.out.println("\n========== 2. 动态 SQL ==========");
        try (SqlSession session = factory.openSession()) {
            DeviceMapper mapper = session.getMapper(DeviceMapper.class);

            System.out.println("type='PLC':");
            mapper.findByCondition("PLC", null, null)
                    .forEach(d -> System.out.println("  " + d));

            System.out.println("\nstatus='ONLINE':");
            mapper.findByCondition(null, "ONLINE", null)
                    .forEach(d -> System.out.println("  " + d));

            System.out.println("\nkeyword='一号':");
            mapper.findByCondition(null, null, "一号")
                    .forEach(d -> System.out.println("  " + d));
        }
    }

    /** 练习3: 批量插入 */
    static void batchInsert() {
        System.out.println("\n========== 3. 批量插入 ==========");
        try (SqlSession session = factory.openSession(true)) {
            DeviceMapper mapper = session.getMapper(DeviceMapper.class);
            List<Device> batch = new ArrayList<>();
            batch.add(buildDevice("批量设备-01", "网关", "机房", "ONLINE"));
            batch.add(buildDevice("批量设备-02", "传感器", "机房", "OFFLINE"));
            batch.add(buildDevice("批量设备-03", "传感器", "机房", "ONLINE"));
            int rows = mapper.batchInsert(batch);
            System.out.println("批量插入 " + rows + " 条");
        }
    }

    /** 练习4: 分页查询 */
    static void pagination() {
        System.out.println("\n========== 4. 分页查询 ==========");
        try (SqlSession session = factory.openSession()) {
            DeviceMapper mapper = session.getMapper(DeviceMapper.class);
            long total = mapper.count();
            int pageSize = 3;
            int totalPages = (int) Math.ceil((double) total / pageSize);
            System.out.println("总数=" + total + ", 每页=" + pageSize + ", 共" + totalPages + "页");

            for (int page = 1; page <= totalPages; page++) {
                int offset = (page - 1) * pageSize;
                RowBounds rb = new RowBounds(offset, pageSize);
                List<Device> pageDevices = session.selectList(
                        "code.day19.DeviceMapper.findAll", null, rb);
                System.out.println("  第" + page + "页: " + pageDevices);
            }
        }
    }

    static Device buildDevice(String name, String type, String loc, String status) {
        Device d = new Device();
        d.setName(name); d.setType(type); d.setLocation(loc); d.setStatus(status);
        return d;
    }
}

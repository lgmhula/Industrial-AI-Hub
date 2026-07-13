package code.day12;

package code.day12;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Day 12：异常处理。
 *
 * <p>覆盖 try-catch-finally、throws、自定义异常和异常链（cause）。
 * 场景：读取并校验工业设备的 CSV 数据。</p>
 */
public class Day12_ExceptionHandling {

    public static void main(String[] args) {
        runtimeExceptionDemo();
        tryCatchFinallyDemo();
        deviceImportDemo();
        exceptionChainDemo();
        multiCatchDemo();
    }

    // ==================== 常见运行时异常 ====================

    private static void runtimeExceptionDemo() {
        System.out.println("========== 常见运行时异常 ==========");

        try {
            String deviceName = null;
            System.out.println(deviceName.length());
        } catch (NullPointerException e) {
            System.out.println("捕获 NullPointerException：对象为 null 时不能调用方法。");
        }

        try {
            int[] temperatures = {36, 37, 38};
            System.out.println(temperatures[3]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("捕获 ArrayIndexOutOfBoundsException：数组下标越界。");
        }

        try {
            Object value = "online";
            Integer statusCode = (Integer) value;
            System.out.println(statusCode);
        } catch (ClassCastException e) {
            System.out.println("捕获 ClassCastException：对象实际类型与目标类型不兼容。");
        }

        try {
            int count = Integer.parseInt("ten");
            System.out.println(count);
        } catch (NumberFormatException e) {
            System.out.println("捕获 NumberFormatException：字符串不是合法整数。");
        }
    }

    // ==================== try-catch-finally 顺序 ====================

    private static void tryCatchFinallyDemo() {
        System.out.println("\n========== try-catch-finally 执行顺序 ==========");
        System.out.println("场景一：try 中没有异常");
        demonstrateFinally(false);

        System.out.println("场景二：try 中发生异常");
        demonstrateFinally(true);

        System.out.println("结论：无论是否捕获异常，finally 通常都会执行，适合释放资源。");
    }

    private static void demonstrateFinally(boolean throwError) {
        try {
            System.out.println("1. 进入 try");
            if (throwError) {
                throw new IllegalStateException("设备连接已断开");
            }
            System.out.println("2. try 正常结束");
        } catch (IllegalStateException e) {
            System.out.println("3. 进入 catch：" + e.getMessage());
        } finally {
            System.out.println("4. 执行 finally：关闭连接或清理临时状态");
        }
    }

    // ==================== throws 与自定义异常 ====================

    private static void deviceImportDemo() {
        System.out.println("\n========== 自定义异常 + throws ==========");
        DeviceDataService service = new DeviceDataService();
        List<String> rows = Arrays.asList(
                "device-001,36.8,ONLINE",
                "device-002,120.5,ONLINE",
                "device-003,37.2,OFFLINE"
        );

        List<DeviceReading> validReadings = new ArrayList<>();
        for (String row : rows) {
            try {
                DeviceReading reading = service.parseAndValidate(row);
                validReadings.add(reading);
                System.out.println("导入成功：" + reading);
            } catch (InvalidDeviceDataException e) {
                System.out.println("导入失败：" + e.getMessage());
            }
        }
        System.out.println("有效数据数量：" + validReadings.size());
    }

    // ==================== 异常链（cause） ====================

    private static void exceptionChainDemo() {
        System.out.println("\n========== 异常链（cause） ==========");
        DeviceDataService service = new DeviceDataService();

        try {
            service.parseAndValidate("device-004,not-a-number,ONLINE");
        } catch (InvalidDeviceDataException e) {
            System.out.println("上层异常：" + e.getMessage());
            Throwable cause = e.getCause();
            System.out.println("根本原因：" + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        }
    }

    // ==================== 多重 catch ====================

    private static void multiCatchDemo() {
        System.out.println("\n========== 多重 catch ==========");
        try {
            calculateAverageTemperature(new String[] {"36.5", "error", "37.0"});
        } catch (NumberFormatException | ArithmeticException e) {
            System.out.println("计算失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private static double calculateAverageTemperature(String[] values) {
        if (values.length == 0) {
            throw new ArithmeticException("没有温度数据，不能计算平均值");
        }
        double sum = 0;
        for (String value : values) {
            sum += Double.parseDouble(value);
        }
        return sum / values.length;
    }

    /** 设备业务数据校验服务。 */
    static class DeviceDataService {

        /**
         * 将一行 CSV 转为实体并校验。
         *
         * @throws InvalidDeviceDataException 行格式、设备编号或温度不合法时抛出
         */
        DeviceReading parseAndValidate(String csvRow) throws InvalidDeviceDataException {
            if (csvRow == null || csvRow.isBlank()) {
                throw new InvalidDeviceDataException("设备数据不能为空");
            }

            String[] fields = csvRow.split(",");
            if (fields.length != 3) {
                throw new InvalidDeviceDataException("数据必须包含设备编号、温度、状态：" + csvRow);
            }

            String deviceId = fields[0].trim();
            String temperatureText = fields[1].trim();
            String status = fields[2].trim();

            if (!deviceId.matches("device-\\d{3}")) {
                throw new InvalidDeviceDataException("设备编号格式错误：" + deviceId);
            }
            if (!"ONLINE".equals(status) && !"OFFLINE".equals(status)) {
                throw new InvalidDeviceDataException("未知设备状态：" + status);
            }

            final double temperature;
            try {
                temperature = Double.parseDouble(temperatureText);
            } catch (NumberFormatException e) {
                // 保留原始异常作为 cause，便于上层定位真正原因。
                throw new InvalidDeviceDataException("温度不是数字：" + temperatureText, e);
            }

            if (temperature < -40 || temperature > 100) {
                throw new InvalidDeviceDataException("温度超出设备允许范围 [-40, 100]：" + temperature);
            }
            return new DeviceReading(deviceId, temperature, status);
        }
    }

    /** 业务层自定义受检异常：调用方必须显式处理或继续声明 throws。 */
    static class InvalidDeviceDataException extends Exception {
        InvalidDeviceDataException(String message) {
            super(message);
        }

        InvalidDeviceDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 设备采集值。 */
    static class DeviceReading {
        private final String deviceId;
        private final double temperature;
        private final String status;

        DeviceReading(String deviceId, double temperature, String status) {
            this.deviceId = deviceId;
            this.temperature = temperature;
            this.status = status;
        }

        @Override
        public String toString() {
            return "DeviceReading{deviceId='" + deviceId + "', temperature=" + temperature
                    + ", status='" + status + "'}";
        }
    }
}

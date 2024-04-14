package cn.monitor4all.miaoshaservice.constant;

/**
 * @Author Jusven
 * @Date 2024/3/29 17:18
 */
public enum OrderRecordStatus {
    NOT_SEND(0),
    SEND_SUCCESS(1),
    SEND_FAILED(9);

    private Integer status;

    OrderRecordStatus(int status) {
    }

    public Integer getStatus() {
        return status;
    }

}

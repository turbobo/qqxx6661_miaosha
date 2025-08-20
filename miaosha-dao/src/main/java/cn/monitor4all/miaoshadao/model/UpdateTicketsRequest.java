package cn.monitor4all.miaoshadao.model;

import java.util.List;

/**
 * 管理员批量修改票数请求模型
 */
public class UpdateTicketsRequest {
    
    /**
     * 管理员ID
     */
    private String adminId;
    
    /**
     * 票数修改列表
     */
    private List<TicketUpdate> ticketUpdates;
    
    public UpdateTicketsRequest() {}
    
    public UpdateTicketsRequest(String adminId, List<TicketUpdate> ticketUpdates) {
        this.adminId = adminId;
        this.ticketUpdates = ticketUpdates;
    }
    
    public String getAdminId() {
        return adminId;
    }
    
    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }
    
    public List<TicketUpdate> getTicketUpdates() {
        return ticketUpdates;
    }
    
    public void setTicketUpdates(List<TicketUpdate> ticketUpdates) {
        this.ticketUpdates = ticketUpdates;
    }
    
    @Override
    public String toString() {
        return "UpdateTicketsRequest{" +
                "adminId='" + adminId + '\'' +
                ", ticketUpdates=" + ticketUpdates +
                '}';
    }
    
    /**
     * 单个票数修改信息
     */
    public static class TicketUpdate {
        
        /**
         * 票券日期
         */
        private String date;
        
        /**
         * 新的总票数
         */
        private Integer totalCount;
        
        /**
         * 新的剩余票数
         */
        private Integer remainingCount;
        
        /**
         * 票券名称（可选，用于更新）
         */
        private String name;
        
        public TicketUpdate() {}
        
        public TicketUpdate(String date, Integer totalCount, Integer remainingCount) {
            this.date = date;
            this.totalCount = totalCount;
            this.remainingCount = remainingCount;
        }
        
        public TicketUpdate(String date, Integer totalCount, Integer remainingCount, String name) {
            this.date = date;
            this.totalCount = totalCount;
            this.remainingCount = remainingCount;
            this.name = name;
        }
        
        public String getDate() {
            return date;
        }
        
        public void setDate(String date) {
            this.date = date;
        }
        
        public Integer getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }
        
        public Integer getRemainingCount() {
            return remainingCount;
        }
        
        public void setRemainingCount(Integer remainingCount) {
            this.remainingCount = remainingCount;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "TicketUpdate{" +
                    "date='" + date + '\'' +
                    ", totalCount=" + totalCount +
                    ", remainingCount=" + remainingCount +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}

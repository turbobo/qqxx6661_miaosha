package cn.monitor4all.miaoshaservice.service;

public interface UserService {

    /**
     * 获取用户验证Hash
     * @param sid
     * @param userId
     * @return
     * @throws Exception
     */
    public String getVerifyHash(Integer sid, Long userId) throws Exception;

    /**
     * 获取用户验证Hash
     * @param date 票券日期
     * @param userId
     * @return
     * @throws Exception
     */
    public String getVerifyHash4Ticket(String date, Long userId) throws Exception;

    /**
     * 添加用户访问次数
     * @param userId
     * @return
     * @throws Exception
     */
    public int addUserCount(Long userId) throws Exception;

    /**
     * 检查用户是否被禁
     * @param userId
     * @return
     */
    public boolean getUserIsBanned(Long userId);


    public void validUser(Long userId) throws Exception;
}

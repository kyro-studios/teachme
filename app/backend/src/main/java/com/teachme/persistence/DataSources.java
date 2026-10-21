package com.teachme.persistence;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/** PostgreSQL への接続の元 (DataSource) を作る。 */
public final class DataSources {
  private DataSources() {}

  /**
   * 接続のプールは持たない。MVP の規模 (利用者は数十人から 100 人、インスタンスは最大 2) では、接続ごとに開き直しても足りるため。
   *
   * @param url JDBC の URL (`jdbc:postgresql://...`)
   */
  public static DataSource postgres(String url, String user, String password) {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(url);
    dataSource.setUser(user);
    dataSource.setPassword(password);
    return dataSource;
  }
}

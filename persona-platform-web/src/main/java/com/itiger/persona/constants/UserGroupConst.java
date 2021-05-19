package com.itiger.persona.constants;

import com.itiger.persona.parser.SqlIdentifier;

/**
 * @author tiny.wang
 */
public class UserGroupConst {

    public static final SqlIdentifier SOURCE_TABLE_IDENTIFIER = new SqlIdentifier("v1", "t_hive_user_signature_data_v1");

    public static final String QUERY_SOURCE_TABLE_PARTITIONS = "show partitions t_hive_user_signature_data_v1 PARTITION(account_type = 'BUS')";

    public static final String SOURCE_TABLE_DT_PARTITION = "dt";

    public static final String SOURCE_TABLE_ACCOUNT_TYPE_PARTITION = "account_type";

    public static final String DT_PARTITION_PREFIX = "dt=";

    public static final String UUID = "uuid";

    public static final String BUS = "bus";

    public static final String IB = "ib";

    public static final String COMMON = "common";

    public static final String PLACEHOLDER_UDF_NAME = "$udfName";

}

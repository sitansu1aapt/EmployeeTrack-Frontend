package com.yatri

object UserContext {
    @Volatile var userId: String? = null
    @Volatile var userEmail: String? = null
    @Volatile var userName: String? = null
    @Volatile var roleName: String? = null
    @Volatile var orgId: String? = null
    @Volatile var siteId: String? = null
    @Volatile var deptId: String? = null
}



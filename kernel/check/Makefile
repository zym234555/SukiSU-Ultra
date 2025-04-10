# 该功能在 linux 5.0-rc1 中引入
ifeq ($(shell grep -q "get_cred_rcu" $(srctree)/include/linux/cred.h; echo $$?),0)
ccflags-y += -DKSU_COMPAT_HAS_GET_CRED_RCU
else
ifeq ($(shell grep -q "atomic_long_t\s\+\usage" $(srctree)/include/linux/cred.h; echo $$?),0)
ccflags-y += -DKSU_COMPAT_ATOMIC_LONG
endif
ifeq ($(shell grep -q "int\s\+\non_rcu" $(srctree)/include/linux/cred.h; echo $$?),0)
ccflags-y += -DKSU_COMPAT_HAS_NONCONST_CRED
endif
endif

# 处理可选的backport
ifeq ($(shell grep -q "strncpy_from_user_nofault" $(srctree)/include/linux/uaccess.h; echo $$?),0)
ccflags-y += -DKSU_STRNCPY_FROM_USER_NOFAULT
endif

ifeq ($(shell grep -q "ssize_t kernel_read" $(srctree)/fs/read_write.c; echo $$?),0)
ccflags-y += -DKSU_KERNEL_READ
endif

ifeq ($(shell grep "ssize_t kernel_write" $(srctree)/fs/read_write.c | grep -q "const void" ; echo $$?),0)
ccflags-y += -DKSU_KERNEL_WRITE
endif

# 检查三星 UH 驱动程序
ifeq ($(shell grep -q "CONFIG_KDP_CRED" $(srctree)/kernel/cred.c; echo $$?),0)
ccflags-y += -DSAMSUNG_UH_DRIVER_EXIST
endif


BackPort := $(strip $(shell \
    if [ "$(VERSION)" -ge "5" -a "$(PATCHLEVEL)" -ge "10" ]; then \
      echo FALSE; \
    else \
      echo TRUE; \
    fi \
))
ifeq ($(BackPort),FALSE)
define check_file_exists
    if [ ! -f $(1) ]; then \
        echo "-- SukiSU: Error: File '$(1)' does not exist"; \
        exit 1; \
    fi
endef

define check_function_exists
    $(call check_file_exists, $(1))
    grep -Pq '^\s*static\s+inline\s+const\s+struct\s+cred\s+\*\s+get_cred_rcu\s*\(' $(1)
endef

define check_code_block
    $(call check_file_exists, $(1))
    grep -Fq "$(2)" $(1)
endef


define insert_code_block
    $(call check_file_exists, $(1))
    $(call backup_file, $(1))
    if ! $(call check_code_block, $(1), $(2)); then \
        echo "-- SukiSU: Inserting code block into $(1)"; \
        sed -i '/^$(3)/i $(2)' $(1); \
    else \
        echo "-- SukiSU: Code block already exists in $(1), skipping"; \
    fi
endef

# 检测原子函数
ifeq ($(shell grep -E 'atomic_inc_not_zero|atomic_long_inc_not_zero' $(srctree)/kernel/cred.c | head -1 | cut -d' ' -f1), atomic_inc_not_zero)
    ATOMIC_INC_FUNC := atomic_inc_not_zero
else ifeq ($(shell grep -E 'atomic_inc_not_zero|atomic_long_inc_not_zero' $(srctree)/kernel/cred.c | head -1 | cut -d' ' -f1), atomic_long_inc_not_zero)
    ATOMIC_INC_FUNC := atomic_long_inc_not_zero
else
    $(info -- SukiSU: Neither atomic_inc_not_zero nor atomic_long_inc_not_zero found in kernel/cred.c)
    $(error Aborting due to unsupported atomic function)
endif

$(info -- SukiSU: Using $(ATOMIC_INC_FUNC) in get_cred_rcu patch.)

# 定义get_cred_rcu函数
GET_CRED_RCU := static inline const struct cred *get_cred_rcu(const struct cred *cred)\n\
{\n\t\
        struct cred *nonconst_cred = (struct cred *) cred;\n\t\
        if (!cred)\n\t\t\
                return NULL;\n\t\
        if (!$(ATOMIC_INC_FUNC)(&nonconst_cred->usage))\n\t\t\
                return NULL;\n\t\
        validate_creds(cred);\n\t\
        return cred;\n\
}\n

# 插入get_cred_rcu函数
ifneq ($(shell $(call check_function_exists, $(srctree)/include/linux/cred.h); echo $$?),0)
    $(info -- SukiSU: Adding function 'get_cred_rcu' to $(srctree)/include/linux/cred.h)
    $(call insert_code_block, $(srctree)/include/linux/cred.h, $(GET_CRED_RCU), static inline void put_cred)
endif

# 修改get_task_cred函数
$(info -- SukiSU: Modifying 'get_task_cred' function in $(srctree)/kernel/cred.c)
$(shell sed -i "s/!$(ATOMIC_INC_FUNC)(&((struct cred \*)cred)->usage)/!get_cred_rcu(cred)/g" $(srctree)/kernel/cred.c)

# 定义can_umount函数
CAN_UMOUNT := static int can_umount(const struct path *path, int flags)\n\
{\n\t\
        struct mount *mnt = real_mount(path->mnt);\n\t\
        if (flags & ~(MNT_FORCE | MNT_DETACH | MNT_EXPIRE | UMOUNT_NOFOLLOW))\n\t\t\
                return -EINVAL;\n\t\
        if (!may_mount())\n\t\t\
                return -EPERM;\n\t\
        if (path->dentry != path->mnt->mnt_root)\n\t\t\
                return -EINVAL;\n\t\
        if (!check_mnt(mnt))\n\t\t\
                return -EINVAL;\n\t\
        if (mnt->mnt.mnt_flags & MNT_LOCKED)\n\t\t\
                return -EINVAL;\n\t\
        if (flags & MNT_FORCE && !capable(CAP_SYS_ADMIN))\n\t\t\
                return -EPERM;\n\t\
        return 0;\n\
}\n

# 插入can_umount函数
ifneq ($(shell grep -Pq '^\s*static\s+int\s+can_umount\s*\(' $(srctree)/fs/namespace.c; echo $$?),0)
    $(info -- SukiSU: Adding function 'can_umount' to $(srctree)/fs/namespace.c)
    $(call insert_code_block, $(srctree)/fs/namespace.c, $(CAN_UMOUNT), static bool is_mnt_ns_file)
endif

# 定义path_umount函数
PATH_UMOUNT := int path_umount(struct path *path, int flags)\n\
{\n\t\
        struct mount *mnt = real_mount(path->mnt);\n\t\
        int ret;\n\t\
        ret = can_umount(path, flags);\n\t\
        if (!ret)\n\t\t\
                ret = do_umount(mnt, flags);\n\t\
        dput(path->dentry);\n\t\
        mntput_no_expire(mnt);\n\t\
        return ret;\n\
}\n

# 插入path_umount函数
ifneq ($(shell grep -Pq '^\s*int\s+path_umount\s*\(' $(srctree)/fs/namespace.c; echo $$?),0)
    $(info -- SukiSU: Adding function 'path_umount' to $(srctree)/fs/namespace.c)
    $(call insert_code_block, $(srctree)/fs/namespace.c, $(PATH_UMOUNT), static bool is_mnt_ns_file)
endif

# 插入path_umount声明
ifneq ($(shell grep -Pq '^\s*int\s+path_umount\s*\(' $(srctree)/fs/internal.h; echo $$?),0)
    $(info -- SukiSU: Adding 'path_umount' declaration to $(srctree)/fs/internal.h)
    $(call insert_code_block, $(srctree)/fs/internal.h, "int path_umount(struct path *path, int flags);", extern void __init mnt_init)
endif

ccflags-y += -DKSU_UMOUNT
endif
#include <linux/export.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>    /* 包含 ARM64 重定位类型定义 */
#include <linux/vmalloc.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/vmalloc.h>
#include <linux/set_memory.h>
#include <linux/version.h>
#include <linux/export.h>
#include <linux/slab.h>
#include "kpm.h"
#include "compact.h"

unsigned long sukisu_compact_find_symbol(const char* name);

// ======================================================================

const char* kpver = "0.10";

struct CompactAddressSymbol {
    const char* symbol_name;
    void* addr;
};

struct CompactAliasSymbol {
    const char* symbol_name;
    const char* compact_symbol_name;
};

struct CompactProxySymbol {
    const char* symbol_name;
    const char* compact_symbol_name;
    void* cached_address;
};

struct CompactAddressSymbol address_symbol [] = {
    { "kallsyms_lookup_name", &kallsyms_lookup_name },
    { "compact_find_symbol", &sukisu_compact_find_symbol },
    { "compat_copy_to_user", &copy_to_user },
    { "compat_strncpy_from_user", &strncpy_from_user },
    { "kpver", &kpver },
    { "is_run_in_sukisu_ultra", (void*)1 }
};

struct CompactAliasSymbol alias_symbol[] = {
    {"compat_copy_to_user", "__arch_copy_to_user"}
};

struct CompactProxySymbol proxy_symbol[] = {
    {"kf_strncat", "strncat", NULL },
    {"kf_strlen", "strlen", NULL },
    {"kf_strcpy", "strcpy", NULL },
};

static unsigned long sukisu_find_proxy_symbol(const char* name) {
    // 查不到就查查兼容的符号
    int i;
    for(i = 0; i < (sizeof(proxy_symbol) / sizeof(struct CompactProxySymbol)); i++) {
        struct CompactProxySymbol* symbol = &alias_symbol[i];
        if(strcmp(name, symbol->symbol_name) == 0) {
            if(symbol->cached_address == NULL) {
                symbol->cached_address = (void*) kallsyms_lookup_name(name);
            }
            if(symbol->cached_address != NULL) {
                return (unsigned long) &symbol->cached_address;
            }
        }
    }
    return 0;
}

unsigned long sukisu_compact_find_symbol(const char* name) {
    int i;
    unsigned long addr;

    // 先自己在地址表部分查出来
    for(i = 0; i < (sizeof(address_symbol) / sizeof(struct CompactAddressSymbol)); i++) {
        struct CompactAddressSymbol* symbol = &address_symbol[i];
        if(strcmp(name, symbol->symbol_name) == 0) {
            return (unsigned long) symbol->addr;
        }
    }

    /* 如果符号名以 "kf__" 开头，尝试解析去掉前缀的部分 */
    if (strncmp(name, "kf__", 4) == 0) {
        addr = sukisu_find_proxy_symbol(name);
        if(addr != 0) {
            return addr;
        }
    }

    // 通过内核来查
    addr = kallsyms_lookup_name(name);
    if(addr) {
        return addr;
    }

    // 查不到就查查兼容的符号
    for(i = 0; i < (sizeof(alias_symbol) / sizeof(struct CompactAliasSymbol)); i++) {
        struct CompactAliasSymbol* symbol = &alias_symbol[i];
        if(strcmp(name, symbol->symbol_name) == 0) {
            addr = kallsyms_lookup_name(symbol->compact_symbol_name);
            if(addr)
                return addr;
        }
    }

    return 0;
}
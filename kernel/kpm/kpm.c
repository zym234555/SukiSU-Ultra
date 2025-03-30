/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2025 Liankong (xhsw.new@outlook.com). All Rights Reserved.
 * 本代码由GPL-2授权
 * 
 * 适配KernelSU的KPM 内核模块加载器兼容实现
 * 
 * 集成了 ELF 解析、内存布局、符号处理、重定位（支持 ARM64 重定位类型）
 * 并参照KernelPatch的标准KPM格式实现加载和控制
 */
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
#include <asm/insn.h>
#include <linux/kprobes.h>
#include <linux/stacktrace.h>
#include <linux/kallsyms.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5,0,0) && defined(CONFIG_MODULES)
#include <linux/moduleloader.h> // 需要启用 CONFIG_MODULES
#endif
#include "kpm.h"
#include "compact.h"

static inline void local_flush_icache_all(void)
{
    asm volatile("ic iallu");
    asm volatile("dsb nsh" : : : "memory");
    asm volatile("isb" : : : "memory");
}

static inline void flush_icache_all(void)
{
    asm volatile("dsb ish" : : : "memory");
    asm volatile("ic ialluis");
    asm volatile("dsb ish" : : : "memory");
    asm volatile("isb" : : : "memory");
}

/**
 * kpm_malloc_exec - 分配可执行内存
 * @size: 需要分配的内存大小（字节）
 *
 * 返回值: 成功返回内存指针，失败返回 NULL
 */
void *kpm_malloc_exec(size_t size)
{
    void *addr = NULL;
    unsigned long nr_pages;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5,0,0)
    /* 内核 5.0+ 方案 */
#if defined(CONFIG_MODULES)
    // 使用 module_alloc + set_memory_x
    addr = module_alloc(size);
    if (!addr)
        return NULL;

    nr_pages = DIV_ROUND_UP(size, PAGE_SIZE);
    if (set_memory_x((unsigned long)addr, nr_pages)) {
        vfree(addr); // 注意：某些内核版本用 module_memfree
        return NULL;
    }
#else
    // 如果未启用模块支持，回退到 vmalloc + 手动设置权限（可能有安全风险）
    addr = __vmalloc(size, GFP_KERNEL, PAGE_KERNEL_EXEC);
    if (addr) {
        nr_pages = DIV_ROUND_UP(size, PAGE_SIZE);
        if (set_memory_x((unsigned long)addr, nr_pages)) {
            vfree(addr);
            addr = NULL;
        }
    }
#endif
#else
    /* 内核 <5.0 方案 */
#if defined(vmalloc_exec)
    // 旧版直接使用 vmalloc_exec
    addr = vmalloc_exec(size);
#else
    // 兼容某些旧版本变种
    addr = __vmalloc(size, GFP_KERNEL, PAGE_KERNEL_EXEC);
#endif
#endif

    flush_icache_all();

    return addr;
}

/**
 * kpm_free_exec - 释放可执行内存
 * @addr: alloc_exec_memory 返回的指针
 * @size: 分配时的大小
 */
void kpm_free_exec(void *addr, size_t size)
{
    unsigned long nr_pages;
    if (!addr)
        return;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5,0,0)
    nr_pages = DIV_ROUND_UP(size, PAGE_SIZE);
    set_memory_nx((unsigned long)addr, nr_pages);
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5,0,0) && defined(CONFIG_MODULES)
    module_memfree(addr); // 5.0+ 且启用模块支持
#else
    vfree(addr); // 旧版或未启用模块
#endif
}


/* KPM 模块头部结构体，存放于 ELF 的 .kpm 段中 */
struct kpm_header {
    u32 magic;  /* 魔数，要求为 'KPM' -> 0x4B504D */
    u32 version;/* 版本号，目前要求为 1 */
    int (*entry)(const char *args, const char *event, void *__user reserved);
    void (*exit)(void *__user reserved);
};

#define KPM_MAGIC   0x4B504D
#define KPM_VERSION 1

/* 加载信息结构体，避免与内核已有 load_info 冲突 */
struct kpm_load_info {
    const void *hdr;           /* ELF 数据 */
    Elf64_Ehdr *ehdr;          /* ELF 头 */
    Elf64_Shdr *sechdrs;       /* 段表 */
    const char *secstrings;    /* 段名字符串表 */
    size_t len;                /* 文件长度 */
    struct {
        const char *base;
        const char *name;
        const char *version;
        const char *license;
        const char *author;
        const char *description;
        size_t size;
    } info;
    struct {
        int info;
        int sym;
        int str;
    } index;
    char *strtab;              /* 符号表对应的字符串表 */
    unsigned long symoffs, stroffs;
};

/* 模块数据结构，改名为 kpm_module 避免冲突 */
struct kpm_module {
    struct list_head list;
    char *args;
    char *ctl_args;
    void *start;                 /* 分配的连续内存区域 */
    unsigned int size;           /* 总大小 */
    unsigned int text_size;
    unsigned int ro_size;
    int (*init)(const char *args, const char *event, void *__user reserved);
    void (*exit)(void *__user reserved);
    int (*ctl0)(const char *ctl_args, char *__user out_msg, int outlen);
    int (*ctl1)(void *a1, void *a2, void *a3);
    struct {
        const char *base;
        const char *name;
        const char *version;
        const char *license;
        const char *author;
        const char *description;
    } info;
};

/* 全局模块列表，改名为 kpm_module_list */
static LIST_HEAD(kpm_module_list);
static DEFINE_SPINLOCK(kpm_module_lock);

/*-----------------------------------------------------------
 * ELF 头和段表解析（针对 ARM64 检查）
 *----------------------------------------------------------*/
#define kpm_elf_check_arch(x) ((x)->e_machine == EM_AARCH64)

static int kpm_elf_header_check(struct kpm_load_info *info)
{
    if (info->len < sizeof(Elf64_Ehdr))
        return -EINVAL;
    info->ehdr = (Elf64_Ehdr *)info->hdr;
    if (memcmp(info->ehdr->e_ident, ELFMAG, SELFMAG) != 0)
        return -EINVAL;
    if (info->ehdr->e_shoff + info->ehdr->e_shnum * sizeof(Elf64_Shdr) > info->len)
        return -EINVAL;
    info->sechdrs = (Elf64_Shdr *)((const char *)info->hdr + info->ehdr->e_shoff);
    if (info->ehdr->e_shstrndx >= info->ehdr->e_shnum)
        return -EINVAL;
    info->secstrings = (const char *)info->hdr + info->sechdrs[info->ehdr->e_shstrndx].sh_offset;
    return 0;
}

/* 在 ELF 文件中查找指定段 */
Elf64_Shdr *find_sec(struct kpm_load_info *info, const char *sec_name)
{
    Elf64_Ehdr *ehdr = info->ehdr;
    Elf64_Shdr *shdr = (Elf64_Shdr *)((char *)ehdr + ehdr->e_shoff);
    const char *shstrtab = (char *)ehdr + shdr[ehdr->e_shstrndx].sh_offset;
    int i;

    for (i = 0; i < ehdr->e_shnum; i++) {
        if (strcmp(shstrtab + shdr[i].sh_name, sec_name) == 0)
            return &shdr[i];
    }

    return NULL;
}

int find_sec_num(struct kpm_load_info *info, const char *sec_name) {
    Elf64_Ehdr *ehdr = info->ehdr;
    Elf64_Shdr *shdr = (Elf64_Shdr *)((char *)ehdr + ehdr->e_shoff);
    const char *shstrtab = (char *)ehdr + shdr[ehdr->e_shstrndx].sh_offset;
    int i;

    for (i = 0; i < ehdr->e_shnum; i++) {
        if (strcmp(shstrtab + shdr[i].sh_name, sec_name) == 0)
            return i;
    }

    return -1;
}

/*-----------------------------------------------------------
 * 模块 modinfo 提取
 *----------------------------------------------------------*/
static char *kpm_next_string(char *string, unsigned long *secsize)
{
    while (string[0]) {
        string++;
        if ((*secsize)-- <= 1)
            return NULL;
    }
    while (!string[0]) {
        string++;
        if ((*secsize)-- <= 1)
            return NULL;
    }
    return string;
}

static char *kpm_get_next_modinfo(const struct kpm_load_info *info, const char *tag, char *prev)
{
    char *p;
    unsigned int taglen = strlen(tag);
    Elf_Shdr *infosec = &info->sechdrs[info->index.info];
    unsigned long size = infosec->sh_size;
    char *modinfo = (char *)info->hdr + infosec->sh_offset;
    if (prev) {
        size -= prev - modinfo;
        modinfo = kpm_next_string(prev, &size);
    }
    for (p = modinfo; p; p = kpm_next_string(p, &size)) {
        if (strncmp(p, tag, taglen) == 0 && p[taglen] == '=')
            return p + taglen + 1;
    }
    return NULL;
}

static char *kpm_get_modinfo(const struct kpm_load_info *info, const char *tag)
{
    return kpm_get_next_modinfo(info, tag, NULL);
}

/*-----------------------------------------------------------
 * 内存布局与段复制
 *----------------------------------------------------------*/
static long kpm_get_offset(struct kpm_module *mod, unsigned int *size, Elf64_Shdr *sechdr)
{
    long ret = ALIGN(*size, sechdr->sh_addralign ? sechdr->sh_addralign : 1);
    *size = ret + sechdr->sh_size;
    return ret;
}

/*static void kpm_layout_sections(struct kpm_module *mod, struct kpm_load_info *info)
{
    int i;
    for (i = 0; i < info->ehdr->e_shnum; i++)
        info->sechdrs[i].sh_entsize = ~0UL;
    
    for (i = 0; i < info->ehdr->e_shnum; i++) {
        Elf64_Shdr *s = &info->sechdrs[i];
        if (!(s->sh_flags & SHF_ALLOC))
            continue;
        s->sh_entsize = kpm_get_offset(mod, &mod->size, s);
    }

    mod->size = ALIGN(mod->size, 8);
}*/

#ifndef ARCH_SHF_SMALL
#define ARCH_SHF_SMALL 0
#endif

#ifndef align
#define KP_ALIGN_MASK(x, mask) (((x) + (mask)) & ~(mask))
#define KP_ALIGN(x, a) ALIGN_MASK(x, (typeof(x))(a)-1)
#define kp_align(X) KP_ALIGN(X, page_size)
#endif

static void kpm_layout_sections(struct kpm_module *mod, struct kpm_load_info *info)
{
    static unsigned long const masks[][2] = {
        /* NOTE: all executable code must be the first section in this array; otherwise modify the text_size finder in the two loops below */
        { SHF_EXECINSTR | SHF_ALLOC, ARCH_SHF_SMALL },
        { SHF_ALLOC, SHF_WRITE | ARCH_SHF_SMALL },
        { SHF_WRITE | SHF_ALLOC, ARCH_SHF_SMALL },
        { ARCH_SHF_SMALL | SHF_ALLOC, 0 }
    };
    int i, m;

    for (i = 0; i < info->ehdr->e_shnum; i++)
        info->sechdrs[i].sh_entsize = ~0UL;

    // todo: tslf alloc all rwx and not page aligned
    for (m = 0; m < sizeof(masks) / sizeof(masks[0]); ++m) {
        for (i = 0; i < info->ehdr->e_shnum; ++i) {
            Elf_Shdr *s = &info->sechdrs[i];
            if ((s->sh_flags & masks[m][0]) != masks[m][0] || (s->sh_flags & masks[m][1]) || s->sh_entsize != ~0UL)
                continue;
            s->sh_entsize = get_offset(mod, &mod->size, s, i);
            // const char *sname = info->secstrings + s->sh_name;
        }
        switch (m) {
        case 0: /* executable */
            mod->size = kp_align(mod->size);
            mod->text_size = mod->size;
            break;
        case 1: /* RO: text and ro-data */
            mod->size = kp_align(mod->size);
            mod->ro_size = mod->size;
            break;
        case 2:
            break;
        case 3: /* whole */
            mod->size = kp_align(mod->size);
            break;
        }
    }
}

/*-----------------------------------------------------------
 * 符号处理与重定位（针对 ARM64）
 *----------------------------------------------------------*/
static bool kpm_is_core_symbol(const Elf64_Sym *src, const Elf64_Shdr *sechdrs, unsigned int shnum)
{
    const Elf64_Shdr *sec;
    if (src->st_shndx == SHN_UNDEF || src->st_shndx >= shnum || !src->st_name)
        return false;
    sec = &sechdrs[src->st_shndx];
    if (!(sec->sh_flags & SHF_ALLOC))
        return false;
    return true;
}

static int kpm_simplify_symbols(struct kpm_module *mod, const struct kpm_load_info *info)
{
    Elf64_Shdr *symsec = &info->sechdrs[info->index.sym];
    Elf64_Sym *sym = (Elf64_Sym *)symsec->sh_addr;
    unsigned long secbase;
    int ret = 0;
    unsigned int i;
    unsigned long addr = 0;

    for (i = 1; i < symsec->sh_size / sizeof(Elf64_Sym); i++) {
        const char *name = info->strtab + sym[i].st_name;
        switch (sym[i].st_shndx) {
        case SHN_COMMON:
            if (!strncmp(name, "__gnu_lto", 9)) {
                printk(KERN_ERR "ARM64 KPM Loader: compile with -fno-common\n");
                ret = -ENOEXEC;
            }
            break;
        case SHN_ABS:
            break;
        case SHN_UNDEF:
            // addr = kallsyms_lookup_name(name);
            addr = sukisu_compact_find_symbol(name);
            if (!addr) {
                printk(KERN_ERR "ARM64 KPM Loader: unknown symbol: %s\n", name);
                ret = -ENOENT;
                break;
            }
            sym[i].st_value = addr;
            break;
        default:
            secbase = info->sechdrs[sym[i].st_shndx].sh_addr;
            sym[i].st_value += secbase;
            break;
        }
    }
    return ret;
}

#ifndef R_AARCH64_GLOB_DAT
#define	R_AARCH64_GLOB_DAT	1025	/* Set GOT entry to data address */
#endif
#ifndef R_AARCH64_JUMP_SLOT
#define	R_AARCH64_JUMP_SLOT	1026	/* Set GOT entry to code address */
#endif
#ifndef R_ARM_NONE
#define R_ARM_NONE 0
#endif
#ifndef R_AARCH64_NONE
#define R_AARCH64_NONE 256
#endif
#ifndef R_AARCH64_GLOB_DAT
#define R_AARCH64_GLOB_DAT    1025    /* Set GOT entry to data address */
#endif
#ifndef R_AARCH64_JUMP_SLOT
#define R_AARCH64_JUMP_SLOT   1026    /* Set GOT entry to code address */
#endif
#ifndef R_ARM_NONE
#define R_ARM_NONE            0
#endif
#ifndef R_AARCH64_NONE
#define R_AARCH64_NONE        256
#endif
#ifndef AARCH64_INSN_IMM_MOVNZ
#define AARCH64_INSN_IMM_MOVNZ AARCH64_INSN_IMM_MAX
#endif
#ifndef AARCH64_INSN_IMM_MOVK
#define AARCH64_INSN_IMM_MOVK AARCH64_INSN_IMM_16
#endif
#ifndef le32_to_cpu
#define le32_to_cpu(x) (x)
#endif
#ifndef cpu_to_le32
#define cpu_to_le32(x) (x)
#endif

enum aarch64_reloc_op
{
    RELOC_OP_NONE,
    RELOC_OP_ABS,
    RELOC_OP_PREL,
    RELOC_OP_PAGE,
};

static u64 do_reloc(enum aarch64_reloc_op reloc_op, void *place, u64 val)
{
    switch (reloc_op) {
    case RELOC_OP_ABS:
        return val;
    case RELOC_OP_PREL:
        return val - (u64)place;
    case RELOC_OP_PAGE:
        return (val & ~0xfff) - ((u64)place & ~0xfff);
    case RELOC_OP_NONE:
        return 0;
    }

    printk(KERN_ERR "do_reloc: unknown relocation operation %d\n", reloc_op);
    return 0;
}

static int reloc_data(enum aarch64_reloc_op op, void *place, u64 val, int len)
{
    u64 imm_mask = (1 << len) - 1;
    s64 sval = do_reloc(op, place, val);

    switch (len) {
    case 16:
        *(s16 *)place = sval;
        break;
    case 32:
        *(s32 *)place = sval;
        break;
    case 64:
        *(s64 *)place = sval;
        break;
    default:
        printk(KERN_ERR "Invalid length (%d) for data relocation\n", len);
        return 0;
    }
    /*
	 * Extract the upper value bits (including the sign bit) and
	 * shift them to bit 0.
	 */
    sval = (s64)(sval & ~(imm_mask >> 1)) >> (len - 1);

    /*
	 * Overflow has occurred if the value is not representable in
	 * len bits (i.e the bottom len bits are not sign-extended and
	 * the top bits are not all zero).
	 */
    if ((u64)(sval + 1) > 2) return -ERANGE;

    return 0;
}

static int reloc_insn_movw(enum aarch64_reloc_op op, void *place, u64 val, int lsb, enum aarch64_insn_imm_type imm_type)
{
    u64 imm, limit = 0;
    s64 sval;
    u32 insn = le32_to_cpu(*(u32 *)place);

    sval = do_reloc(op, place, val);
    sval >>= lsb;
    imm = sval & 0xffff;

    if (imm_type == AARCH64_INSN_IMM_MOVNZ) {
        /*
		 * For signed MOVW relocations, we have to manipulate the
		 * instruction encoding depending on whether or not the
		 * immediate is less than zero.
		 */
        insn &= ~(3 << 29);
        if ((s64)imm >= 0) {
            /* >=0: Set the instruction to MOVZ (opcode 10b). */
            insn |= 2 << 29;
        } else {
            /*
			 * <0: Set the instruction to MOVN (opcode 00b).
			 *     Since we've masked the opcode already, we
			 *     don't need to do anything other than
			 *     inverting the new immediate field.
			 */
            imm = ~imm;
        }
        imm_type = AARCH64_INSN_IMM_MOVK;
    }

    /* Update the instruction with the new encoding. */
    insn = aarch64_insn_encode_immediate(imm_type, insn, imm);
    *(u32 *)place = cpu_to_le32(insn);

    /* Shift out the immediate field. */
    sval >>= 16;

    /*
	 * For unsigned immediates, the overflow check is straightforward.
	 * For signed immediates, the sign bit is actually the bit past the
	 * most significant bit of the field.
	 * The AARCH64_INSN_IMM_16 immediate type is unsigned.
	 */
    if (imm_type != AARCH64_INSN_IMM_16) {
        sval++;
        limit++;
    }

    /* Check the upper bits depending on the sign of the immediate. */
    if ((u64)sval > limit) return -ERANGE;

    return 0;
}

static int reloc_insn_imm(enum aarch64_reloc_op op, void *place, u64 val, int lsb, int len,
                          enum aarch64_insn_imm_type imm_type)
{
    u64 imm, imm_mask;
    s64 sval;
    u32 insn = le32_to_cpu(*(u32 *)place);

    /* Calculate the relocation value. */
    sval = do_reloc(op, place, val);
    sval >>= lsb;
    /* Extract the value bits and shift them to bit 0. */
    imm_mask = (BIT(lsb + len) - 1) >> lsb;
    imm = sval & imm_mask;
    /* Update the instruction's immediate field. */
    insn = aarch64_insn_encode_immediate(imm_type, insn, imm);
    *(u32 *)place = cpu_to_le32(insn);
    /*
	 * Extract the upper value bits (including the sign bit) and
	 * shift them to bit 0.
	 */
    sval = (s64)(sval & ~(imm_mask >> 1)) >> (len - 1);
    /*
	 * Overflow has occurred if the upper bits are not all equal to
	 * the sign bit of the value.
	 */
    if ((u64)(sval + 1) >= 2) return -ERANGE;

    return 0;
}

int kpm_apply_relocate(Elf64_Shdr *sechdrs, const char *strtab, unsigned int symindex, unsigned int relsec,
                   struct kpm_module *me)
{
    return 0;
};

int kpm_apply_relocate_add(Elf64_Shdr *sechdrs, const char *strtab, unsigned int symindex, unsigned int relsec,
                       struct kpm_module *me)
{
    unsigned int i;
    int ovf;
    bool overflow_check;
    Elf64_Sym *sym;
    void *loc;
    u64 val;
    Elf64_Rela *rel = (void *)sechdrs[relsec].sh_addr;

    for (i = 0; i < sechdrs[relsec].sh_size / sizeof(*rel); i++) {
        /* loc corresponds to P in the AArch64 ELF document. */
        loc = (void *)sechdrs[sechdrs[relsec].sh_info].sh_addr + rel[i].r_offset;
        /* sym is the ELF symbol we're referring to. */
        sym = (Elf64_Sym *)sechdrs[symindex].sh_addr + ELF64_R_SYM(rel[i].r_info);
        /* val corresponds to (S + A) in the AArch64 ELF document. */
        val = sym->st_value + rel[i].r_addend;

        overflow_check = true;

        /* Perform the static relocation. */
        switch (ELF64_R_TYPE(rel[i].r_info)) {
        /* Null relocations. */
        case R_ARM_NONE:
        case R_AARCH64_NONE:
            ovf = 0;
            break;
        /* Data relocations. */
        case R_AARCH64_ABS64:
            overflow_check = false;
            ovf = reloc_data(RELOC_OP_ABS, loc, val, 64);
            break;
        case R_AARCH64_ABS32:
            ovf = reloc_data(RELOC_OP_ABS, loc, val, 32);
            break;
        case R_AARCH64_ABS16:
            ovf = reloc_data(RELOC_OP_ABS, loc, val, 16);
            break;
        case R_AARCH64_PREL64:
            overflow_check = false;
            ovf = reloc_data(RELOC_OP_PREL, loc, val, 64);
            break;
        case R_AARCH64_PREL32:
            ovf = reloc_data(RELOC_OP_PREL, loc, val, 32);
            break;
        case R_AARCH64_PREL16:
            ovf = reloc_data(RELOC_OP_PREL, loc, val, 16);
            break;

        /* MOVW instruction relocations. */
        case R_AARCH64_MOVW_UABS_G0_NC:
            overflow_check = false;
        case R_AARCH64_MOVW_UABS_G0:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 0, AARCH64_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G1_NC:
            overflow_check = false;
        case R_AARCH64_MOVW_UABS_G1:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 16, AARCH64_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G2_NC:
            overflow_check = false;
        case R_AARCH64_MOVW_UABS_G2:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 32, AARCH64_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_UABS_G3:
            /* We're using the top bits so we can't overflow. */
            overflow_check = false;
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 48, AARCH64_INSN_IMM_16);
            break;
        case R_AARCH64_MOVW_SABS_G0:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 0, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_SABS_G1:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 16, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_SABS_G2:
            ovf = reloc_insn_movw(RELOC_OP_ABS, loc, val, 32, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G0_NC:
            overflow_check = false;
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 0, AARCH64_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G0:
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 0, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G1_NC:
            overflow_check = false;
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 16, AARCH64_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G1:
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 16, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G2_NC:
            overflow_check = false;
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 32, AARCH64_INSN_IMM_MOVK);
            break;
        case R_AARCH64_MOVW_PREL_G2:
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 32, AARCH64_INSN_IMM_MOVNZ);
            break;
        case R_AARCH64_MOVW_PREL_G3:
            /* We're using the top bits so we can't overflow. */
            overflow_check = false;
            ovf = reloc_insn_movw(RELOC_OP_PREL, loc, val, 48, AARCH64_INSN_IMM_MOVNZ);
            break;
        /* Immediate instruction relocations. */
        case R_AARCH64_LD_PREL_LO19:
            ovf = reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 19, AARCH64_INSN_IMM_19);
            break;
        case R_AARCH64_ADR_PREL_LO21:
            ovf = reloc_insn_imm(RELOC_OP_PREL, loc, val, 0, 21, AARCH64_INSN_IMM_ADR);
            break;
        case R_AARCH64_ADR_PREL_PG_HI21_NC:
            overflow_check = false;
        case R_AARCH64_ADR_PREL_PG_HI21:
            ovf = reloc_insn_imm(RELOC_OP_PAGE, loc, val, 12, 21, AARCH64_INSN_IMM_ADR);
            break;
        case R_AARCH64_ADD_ABS_LO12_NC:
        case R_AARCH64_LDST8_ABS_LO12_NC:
            overflow_check = false;
            ovf = reloc_insn_imm(RELOC_OP_ABS, loc, val, 0, 12, AARCH64_INSN_IMM_12);
            break;
        case R_AARCH64_LDST16_ABS_LO12_NC:
            overflow_check = false;
            ovf = reloc_insn_imm(RELOC_OP_ABS, loc, val, 1, 11, AARCH64_INSN_IMM_12);
            break;
        case R_AARCH64_LDST32_ABS_LO12_NC:
            overflow_check = false;
            ovf = reloc_insn_imm(RELOC_OP_ABS, loc, val, 2, 10, AARCH64_INSN_IMM_12);
            break;
        case R_AARCH64_LDST64_ABS_LO12_NC:
            overflow_check = false;
            ovf = reloc_insn_imm(RELOC_OP_ABS, loc, val, 3, 9, AARCH64_INSN_IMM_12);
            break;
        case R_AARCH64_LDST128_ABS_LO12_NC:
            overflow_check = false;
            ovf = reloc_insn_imm(RELOC_OP_ABS, loc, val, 4, 8, AARCH64_INSN_IMM_12);
            break;
        case R_AARCH64_TSTBR14:
            ovf = reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 14, AARCH64_INSN_IMM_14);
            break;
        case R_AARCH64_CONDBR19:
            ovf = reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 19, AARCH64_INSN_IMM_19);
            break;
        case R_AARCH64_JUMP26:
        case R_AARCH64_CALL26:
            ovf = reloc_insn_imm(RELOC_OP_PREL, loc, val, 2, 26, AARCH64_INSN_IMM_26);
            break;
        default:
            printk(KERN_ERR "unsupported RELA relocation: %llu\n", ELF64_R_TYPE(rel[i].r_info));
            return -ENOEXEC;
        }

        if (overflow_check && ovf == -ERANGE) goto overflow;
    }
    return 0;
overflow:
    printk(KERN_ERR "overflow in relocation type %d val %llx\n", (int)ELF64_R_TYPE(rel[i].r_info), val);
    return -ENOEXEC;
}


static int kpm_apply_relocations(struct kpm_module *mod, const struct kpm_load_info *info)
{
    int rc = 0;
    int i;

    for (i = 1; i < info->ehdr->e_shnum; i++) {
        unsigned int infosec = info->sechdrs[i].sh_info;
        if (infosec >= info->ehdr->e_shnum) continue;
        if (!(info->sechdrs[infosec].sh_flags & SHF_ALLOC)) continue;
        if (info->sechdrs[i].sh_type == SHT_REL) {
            rc = kpm_apply_relocate(info->sechdrs, info->strtab, info->index.sym, i, mod);
        } else if (info->sechdrs[i].sh_type == SHT_RELA) {
            rc = kpm_apply_relocate_add(info->sechdrs, info->strtab, info->index.sym, i, mod);
        }
        if (rc < 0) break;
    }

    return rc;
}


/*-----------------------------------------------------------
 * 符号表与字符串表布局
 *----------------------------------------------------------*/
static void kpm_layout_symtab(struct kpm_module *mod, struct kpm_load_info *info)
{
    Elf64_Shdr *symsect = &info->sechdrs[info->index.sym];
    Elf64_Shdr *strsect = &info->sechdrs[info->index.str];
    const Elf64_Sym *src;
    unsigned int i, nsrc, ndst;
    unsigned int strtab_size = 1;

    symsect->sh_flags |= SHF_ALLOC;
    symsect->sh_entsize = kpm_get_offset(mod, &mod->size, symsect);
    src = (Elf64_Sym *)((char *)info->hdr + symsect->sh_offset);
    nsrc = symsect->sh_size / sizeof(Elf64_Sym);
    for (ndst = i = 0; i < nsrc; i++) {
        if (i == 0 || kpm_is_core_symbol(src + i, info->sechdrs, info->ehdr->e_shnum)) {
            strtab_size += strlen(info->strtab + src[i].st_name) + 1;
            ndst++;
        }
    }
    info->symoffs = ALIGN(mod->size, symsect->sh_addralign ? symsect->sh_addralign : 1);
    info->stroffs = mod->size = info->symoffs + ndst * sizeof(Elf64_Sym);
    mod->size += strtab_size;
    strsect->sh_flags |= SHF_ALLOC;
    strsect->sh_entsize = kpm_get_offset(mod, &mod->size, strsect);
}

/*-----------------------------------------------------------
 * 重写段表头：修正各段的 sh_addr 为在连续内存中的地址
 *----------------------------------------------------------*/
static int kpm_rewrite_section_headers(struct kpm_load_info *info)
{
    int i;
    info->sechdrs[0].sh_addr = 0;
    for (i = 1; i < info->ehdr->e_shnum; i++) {
        Elf64_Shdr *shdr = &info->sechdrs[i];
        if (shdr->sh_type != SHT_NOBITS && info->len < shdr->sh_offset + shdr->sh_size)
            return -ENOEXEC;
        shdr->sh_addr = (size_t)info->hdr + shdr->sh_offset;
    }
    return 0;
}

/*-----------------------------------------------------------
 * 将各段复制到连续内存区域中
 *----------------------------------------------------------*/
/*-----------------------------------------------------------
 * 将各段复制到连续内存区域（修复版）
 * 关键修复点：
 * 1. 段地址按对齐要求正确计算
 * 2. 显式设置可执行内存权限
 * 3. 刷新指令缓存保证一致性
 *----------------------------------------------------------*/
static int kpm_move_module(struct kpm_module *mod, struct kpm_load_info *info)
{
    int i;
    unsigned long curr_offset = 0;
    Elf64_Shdr *shdr;
    void *dest;
    const char *secname;

    /* 分配连续内存（按页对齐） */
    mod->size = ALIGN(mod->size, PAGE_SIZE);
    mod->start = kpm_malloc_exec(mod->size);
    if (!mod->start) {
        printk(KERN_ERR "ARM64 KPM Loader: Failed to allocate module memory\n");
        return -ENOMEM;
    }
    memset(mod->start, 0, mod->size);

    /* 设置内存可执行权限（关键修复） */
    set_memory_x((unsigned long)mod->start, mod->size >> PAGE_SHIFT);
    flush_icache_all();

    printk(KERN_INFO "ARM64 KPM Loader: Final section addresses (aligned base=0x%px):\n", mod->start);

    /* 遍历所有段并按对齐要求布局 */
    for (i = 0; i < info->ehdr->e_shnum; i++) {
        shdr = &info->sechdrs[i];
        if (!(shdr->sh_flags & SHF_ALLOC))
            continue;

        /* 按段对齐要求调整偏移 */
        curr_offset = ALIGN(curr_offset, shdr->sh_addralign);
        dest = mod->start + curr_offset;

        /* 复制段内容（NOBITS 段不复制） */
        if (shdr->sh_type != SHT_NOBITS) {
            memcpy(dest, (void *)shdr->sh_addr, shdr->sh_size);
            
            /* 刷新指令缓存（针对可执行段） */
            if (shdr->sh_flags & SHF_EXECINSTR) {
                flush_icache_range((unsigned long)dest, 
                                 (unsigned long)dest + shdr->sh_size);
            }
        }

        /* 更新段头中的虚拟地址 */
        shdr->sh_addr = (unsigned long)dest;
        curr_offset += shdr->sh_size;

        /* 定位关键函数指针 */
        secname = info->secstrings + shdr->sh_name;
        if (!mod->init && !strcmp(".kpm.init", secname)) {
            mod->init = (int (*)(const char *, const char *, void *__user))dest;
            printk(KERN_DEBUG "Found .kpm.init at 0x%px\n", dest);
        } else if (!strcmp(".kpm.exit", secname)) {
            mod->exit = (void (*)(void *__user))dest;
        }
    }

    /* 调整元数据指针（基于新基址） */
    if (info->info.base) {
        unsigned long delta = (unsigned long)mod->start - (unsigned long)info->hdr;
        mod->info.name = (const char *)((unsigned long)info->info.name + delta);
        mod->info.version = (const char *)((unsigned long)info->info.version + delta);
        if (info->info.license)
            mod->info.license = (const char *)((unsigned long)info->info.license + delta);
        if (info->info.author)
            mod->info.author = (const char *)((unsigned long)info->info.author + delta);
        if (info->info.description)
            mod->info.description = (const char *)((unsigned long)info->info.description + delta);
    }

    flush_icache_all();

    return 0;
}

/*-----------------------------------------------------------
 * 初始化 kpm_load_info：解析 ELF 头、modinfo、符号表
 *----------------------------------------------------------*/
static int kpm_setup_load_info(struct kpm_load_info *info)
{
    int rc = 0;
    int i;
    const char *name;
    const char *version;

    info->sechdrs = (Elf64_Shdr *)((const char *)info->hdr + info->ehdr->e_shoff);
    info->secstrings = (const char *)info->hdr + info->sechdrs[info->ehdr->e_shstrndx].sh_offset;
    rc = kpm_rewrite_section_headers(info);
    if (rc) {
        printk(KERN_ERR "ARM64 KPM Loader: rewrite section headers error\n");
        return rc;
    }
    if (find_sec_num(info, ".kpm.init") == -1 || find_sec_num(info, ".kpm.exit") == -1) {
        printk(KERN_ERR "ARM64 KPM Loader: Missing .kpm.init or .kpm.exit section\n");
        return -ENOEXEC;
    }
    info->index.info = find_sec_num(info, ".kpm.info");
    if (!info->index.info) {
        printk(KERN_ERR "ARM64 KPM Loader: Missing .kpm.info section\n");
        return -ENOEXEC;
    }
    info->info.base = (const char *)info->hdr + info->sechdrs[info->index.info].sh_offset;
    info->info.size = info->sechdrs[info->index.info].sh_entsize;

    name = kpm_get_modinfo(info, "name");
    if (!name) {
        printk(KERN_ERR "ARM64 KPM Loader: Module name not found\n");
        return -ENOEXEC;
    }
    info->info.name = name;
    printk(KERN_INFO "ARM64 KPM Loader: Module name: %s\n", name);

    version = kpm_get_modinfo(info, "version");
    if (!version) {
        printk(KERN_ERR "ARM64 KPM Loader: Module version not found\n");
        return -ENOEXEC;
    }
    info->info.version = version;
    printk(KERN_INFO "ARM64 KPM Loader: Module version: %s\n", version);

    info->info.license = kpm_get_modinfo(info, "license");
    printk(KERN_INFO "ARM64 KPM Loader: Module license: %s\n", info->info.license ? info->info.license : "N/A");

    info->info.author = kpm_get_modinfo(info, "author");
    printk(KERN_INFO "ARM64 KPM Loader: Module author: %s\n", info->info.author ? info->info.author : "N/A");

    info->info.description = kpm_get_modinfo(info, "description");
    printk(KERN_INFO "ARM64 KPM Loader: Module description: %s\n", info->info.description ? info->info.description : "N/A");

    for (i = 1; i < info->ehdr->e_shnum; i++) {
        if (info->sechdrs[i].sh_type == SHT_SYMTAB) {
            info->index.sym = i;
            info->index.str = info->sechdrs[i].sh_link;
            info->strtab = (char *)info->hdr + info->sechdrs[info->index.str].sh_offset;
            break;
        }
    }
    if (info->index.sym == 0) {
        printk(KERN_ERR "ARM64 KPM Loader: Module has no symbols\n");
        return -ENOEXEC;
    }
    return 0;
}

// ============================================================================================

/*-----------------------------------------------------------
 * KPM 模块加载主流程
 *----------------------------------------------------------*/
/* 注意：接口名称改为 kpm_load_module，避免与内核原有 load_module 冲突 */
__nocfi
long kpm_load_module(const void *data, int len, const char *args,
                       const char *event, void *__user reserved)
{
    struct kpm_load_info load_info = { .hdr = data, .len = len };
    long rc = 0;
    struct kpm_module *mod;

    /* 检查 ELF 头 */
    rc = kpm_elf_header_check(&load_info);
    if (rc)
        goto out;

    rc = kpm_setup_load_info(&load_info);
    if (rc)
        goto out;

    /* 检查必须存在的模块初始化/退出段 */
    if (find_sec_num(&load_info, ".kpm.init") == -1 ||
        find_sec_num(&load_info, ".kpm.exit") == -1) {
        printk(KERN_ERR "ARM64 KPM Loader: Required sections missing\n");
        rc = -ENOEXEC;
        goto out;
    }

    /* 检查模块是否已经加载 */
    if (find_module(load_info.info.name)) {
        printk(KERN_ERR "ARM64 KPM Loader: Module %s already loaded\n",
               load_info.info.name);
        rc = -EEXIST;
        goto out;
    }

    mod = vmalloc(sizeof(struct kpm_module));
    if (!mod) {
        rc = -ENOMEM;
        goto out;
    }
    memset(mod, 0, sizeof(struct kpm_module));

    if (args) {
        mod->args = vmalloc(strlen(args) + 1);
        if (!mod->args) {
            rc = -ENOMEM;
            goto free_mod;
        }
        strcpy(mod->args, args);
    }

    kpm_layout_sections(mod, &load_info);
    kpm_layout_symtab(mod, &load_info);

    rc = kpm_move_module(mod, &load_info);
    if (rc)
        goto free_mod;
    rc = kpm_simplify_symbols(mod, &load_info);
    if (rc)
        goto free_mod;
    rc = kpm_apply_relocations(mod, &load_info);
    if (rc)
        goto free_mod;

    /* 替换 flush_icache_all() 为 flush_icache_range() */
    flush_icache_range((unsigned long)mod->start,
                       (unsigned long)mod->start + mod->size);
    flush_icache_all();

    rc = mod->init(mod->args, event, reserved);
    if (!rc) {
        printk(KERN_INFO "ARM64 KPM Loader: Module [%s] loaded successfully with args [%s]\n",
               mod->info.name, args ? args : "");
        spin_lock(&kpm_module_lock);
        list_add_tail(&mod->list, &kpm_module_list);
        spin_unlock(&kpm_module_lock);
        goto out;
    } else {
        printk(KERN_ERR "ARM64 KPM Loader: Module [%s] init failed with error %ld\n",
               mod->info.name, rc);
        mod->exit(reserved);
    }
free_mod:
    if (mod->args)
        vfree(mod->args);
    kpm_free_exec(mod->start, mod->size);
    vfree(mod);
out:
    return rc;
}

/* 卸载模块接口，改名为 sukisu_kpm_unload_module */
__nocfi
long sukisu_kpm_unload_module(const char *name, void *__user reserved)
{
    long rc = 0;
    struct kpm_module *mod = NULL;

    if (!name)
        return -EINVAL;
    spin_lock(&kpm_module_lock);
    
    list_for_each_entry(mod, &kpm_module_list, list) {
        if (!strcmp(name, mod->info.name))
            break;
    }
    if (!mod) {
        rc = -ENOENT;
        spin_unlock(&kpm_module_lock);
        return rc;
    }
    list_del(&mod->list);
    spin_unlock(&kpm_module_lock);
    // rc = mod->exit(reserved);
    mod->exit(reserved);
    if (mod->args)
        vfree(mod->args);
    if (mod->ctl_args)
        vfree(mod->ctl_args);
    kpm_free_exec(mod->start, mod->size);
    vfree(mod);
    printk(KERN_INFO "ARM64 KPM Loader: Module %s unloaded, rc = %ld\n", name, rc);
    return rc;
}

/*-----------------------------------------------------------
 * 导出接口：从文件路径加载 KPM 模块（改名为 sukisu_kpm_load_module_path）
 *----------------------------------------------------------*/
long sukisu_kpm_load_module_path(const char *path, const char *args, void *__user reserved)
{
    long rc = 0;
    struct file *filp;
    loff_t len;
    void *data;
    loff_t pos = 0;

    printk(KERN_INFO "ARM64 KPM Loader: Loading module from file: %s\n", path);
    if (!path)
        return -EINVAL;
    filp = filp_open(path, O_RDONLY, 0);
    if (IS_ERR(filp)) {
        printk(KERN_ERR "ARM64 KPM Loader: Failed to open file %s\n", path);
        return PTR_ERR(filp);
    }
    len = vfs_llseek(filp, 0, SEEK_END);
    printk(KERN_INFO "ARM64 KPM Loader: Module file size: %llx\n", len);
    vfs_llseek(filp, 0, SEEK_SET);
    data = vmalloc(len);
    if (!data) {
        filp_close(filp, NULL);
        return -ENOMEM;
    }
    memset(data, 0, len);
    kernel_read(filp, data, len, &pos);
    filp_close(filp, 0);
    if (pos != len) {
        printk(KERN_ERR "ARM64 KPM Loader: Read file error\n");
        rc = -EIO;
        goto free_data;
    }
    rc = kpm_load_module(data, len, args, "load-file", reserved);
free_data:
    vfree(data);
    return rc;
}

/*-----------------------------------------------------------
 * 模块管理查询接口
 *----------------------------------------------------------*/
struct kpm_module *sukisu_kpm_find_module(const char *name)
{
    struct kpm_module *pos;
    spin_lock(&kpm_module_lock);
    list_for_each_entry(pos, &kpm_module_list, list) {
        if (!strcmp(name, pos->info.name)) {
            spin_unlock(&kpm_module_lock);
            return pos;
        }
    }
    spin_unlock(&kpm_module_lock);
    return NULL;
}

// 获取已经加载的KPM数量
int sukisu_kpm_num(void) {
    struct kpm_module *pos;
    int num = 0;

    spin_lock(&kpm_module_lock);
    list_for_each_entry(pos, &kpm_module_list, list) {
        num++;
    }
    spin_unlock(&kpm_module_lock);
    
    return num;
}

// 获取指定名称的KPM信息
int sukisu_kpm_info(const char* name, char __user* out) {
    char buffer[512] = { 0 };
    struct kpm_module *kpm = sukisu_kpm_find_module(name);
    int osize;

    if(kpm == NULL) {
        return -1;
    }

    memset((void*)&buffer, 0, sizeof(buffer));
    osize = snprintf(buffer, 511,
        "Name: %s\n"
        "Version: %s\n"
        "Author: %s\n"
        "License: %s\n"
        "Description: %s",
        kpm->info.name, kpm->info.version, kpm->info.author, kpm->info.license, kpm->info.description);
    
    osize = copy_to_user((void __user*) out, (const void*)buffer, osize + 1);
    return osize;
}

int sukisu_kpm_list(char __user *out,unsigned int bufferSize)
{
    struct kpm_module *pos;
    int outSize = 0;
    int len;
    char buffer[128]; // 临时缓冲区，避免直接操作用户空间

    spin_lock(&kpm_module_lock);
    list_for_each_entry(pos, &kpm_module_list, list) {
        /* 格式化输出，每行一个模块名称 */
        len = snprintf(buffer, sizeof(buffer), "%s\n", pos->info.name);

        /* 检查剩余空间是否足够 */
        if (outSize + len > bufferSize) {
            spin_unlock(&kpm_module_lock);
            return -ENOSPC;  // 空间不足
        }

        /* 复制到用户空间 */
        if (copy_to_user(out + outSize, buffer, len)) {
            spin_unlock(&kpm_module_lock);
            return -EFAULT;  // 复制失败
        }

        outSize += len;
    }
    spin_unlock(&kpm_module_lock);

    return outSize;
}

// 打印所有KPM信息
/* 直接写入进程 stdout（fd = 1） */
void sukisu_kpm_print_list(void)
{
    struct kpm_module *kpm;
    struct file *stdout_file;
    loff_t pos = 0;
    char *buffer;
    int len;

    /* 打开当前进程的 stdout */
    stdout_file = filp_open("/proc/self/fd/1", O_WRONLY, 0);
    if (IS_ERR(stdout_file)) {
        printk(KERN_ERR "sukisu_kpm_print_list: Failed to open stdout.\n");
        return;
    }

    /* 分配内核缓冲区 */
    buffer = kmalloc(256, GFP_KERNEL);
    if (!buffer) {
        printk(KERN_ERR "sukisu_kpm_print_list: Failed to allocate buffer.\n");
        filp_close(stdout_file, NULL);
        return;
    }

    spin_lock(&kpm_module_lock);
    list_for_each_entry(kpm, &kpm_module_list, list) {
        /* 格式化模块信息 */
        len = snprintf(buffer, 256,
            "Name: %s\n"
            "Version: %s\n"
            "Author: %s\n"
            "License: %s\n"
            "Description: %s\n\n",
            kpm->info.name, kpm->info.version, kpm->info.author,
            kpm->info.license, kpm->info.description);

        /* 通过 kernel_write() 直接写入 stdout */
        kernel_write(stdout_file, buffer, len, &pos);
    }
    spin_unlock(&kpm_module_lock);

    /* 释放资源 */
    kfree(buffer);
    filp_close(stdout_file, NULL);
}

EXPORT_SYMBOL(sukisu_kpm_load_module_path);
EXPORT_SYMBOL(sukisu_kpm_unload_module);
EXPORT_SYMBOL(sukisu_kpm_find_module);

// ===========================================================================================

/*--------------------- 地址过滤逻辑 ---------------------*/
/**
 * is_allow_address - 自定义地址放行规则
 * @addr: 目标函数地址
 * 
 * 返回值: true 放行 | false 拦截
 */
bool kpm_is_allow_address(unsigned long addr)
{
    struct kpm_module *pos;
    bool allow = false;

    spin_lock(&kpm_module_lock);
    list_for_each_entry(pos, &kpm_module_list, list) {
        unsigned long start_address = (unsigned long) pos->start;
        unsigned long end_address = start_address + pos->size;

        /* 规则1：地址在KPM允许范围内 */
        if (addr >= start_address && addr <= end_address) {
            allow = true;
            break;
        }
    }
    spin_unlock(&kpm_module_lock);

    // TODO: 增加Hook跳板放行机制

    return allow;
}
// ============================================================================================

int sukisu_handle_kpm(unsigned long arg3, unsigned long arg4, unsigned long arg5)
{
    if(arg3 == SUKISU_KPM_LOAD) {
        char kernel_load_path[256] = { 0 };
        char kernel_args_buffer[256] = { 0 };

        if(arg4 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_load_path, (const char __user *)arg4, 255);
        if(arg5 != 0) {
            strncpy_from_user((char*)&kernel_args_buffer, (const char __user *)arg4, 255);
        }
        return sukisu_kpm_load_module_path((const char*)&kernel_load_path, (const char*) &kernel_args_buffer, NULL);
    } else if(arg3 == SUKISU_KPM_UNLOAD) {
        char kernel_name_buffer[256] = { 0 };

        if(arg4 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_name_buffer, (const char __user *)arg4, 255);
        return sukisu_kpm_unload_module((const char*) &kernel_name_buffer, NULL);
    } else if(arg3 == SUKISU_KPM_NUM) {
        return sukisu_kpm_num();
    } else if(arg3 == SUKISU_KPM_INFO) {
        char kernel_name_buffer[256] = { 0 };

        if(arg4 == 0 || arg5 == 0) {
            return -1;
        }
        
        strncpy_from_user((char*)&kernel_name_buffer, (const char __user *)arg4, 255);
        return sukisu_kpm_info((const char*) &kernel_name_buffer, (char __user*) arg5);
    } else if(arg3 == SUKISU_KPM_LIST) {
        return sukisu_kpm_list((char __user*) arg4, (unsigned int) arg5);
    } else if(arg3 == SUKISU_KPM_PRINT) {
        sukisu_kpm_print_list();
    }
    return 0;
}

int sukisu_is_kpm_control_code(unsigned long arg2) {
    return (arg2 >= CMD_KPM_CONTROL && arg2 <= CMD_KPM_CONTROL_MAX) ? 1 : 0;
}

EXPORT_SYMBOL(sukisu_handle_kpm);
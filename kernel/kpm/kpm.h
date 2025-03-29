#ifndef ___SUKISU_KPM_H
#define ___SUKISU_KPM_H

int sukisu_handle_kpm(unsigned long arg3, unsigned long arg4, unsigned long arg5);
int sukisu_is_kpm_control_code(unsigned long arg2);

// KPM控制代码
#define CMD_KPM_CONTROL 28
#define CMD_KPM_CONTROL_MAX 34

// 控制代码

// prctl(xxx, xxx, 1, "PATH", "ARGS")
// success return 0, error return -N
#define SUKISU_KPM_LOAD 28

// prctl(xxx, xxx, 2, "NAME")
// success return 0, error return -N
#define SUKISU_KPM_UNLOAD 29

// num = prctl(xxx, xxx, 3)
// error return -N
// success return +num or 0
#define SUKISU_KPM_NUM 30

// prctl(xxx, xxx, 4, Buffer, BufferSize)
// success return +out, error return -N
#define SUKISU_KPM_LIST 31

// prctl(xxx, xxx, 5, "NAME", Buffer[256])
// success return +out, error return -N
#define SUKISU_KPM_INFO 32

// prctl(xxx, xxx, 6, "NAME", "ARGS")
// success return KPM's result value
// error return -N
#define SUKISU_KPM_CONTROL 33

// prctl(xxx, xxx, 7)
// success will printf to stdout and return 0
// error will return -1
#define SUKISU_KPM_PRINT 34


/* A64 instructions are always 32 bits. */
#define AARCH64_INSN_SIZE 4

/*
 * ARM Architecture Reference Manual for ARMv8 Profile-A, Issue A.a
 * Section C3.1 "A64 instruction index by encoding":
 * AArch64 main encoding table
 *  Bit position
 *   28 27 26 25	Encoding Group
 *   0  0  -  -		Unallocated
 *   1  0  0  -		Data processing, immediate
 *   1  0  1  -		Branch, exception generation and system instructions
 *   -  1  -  0		Loads and stores
 *   -  1  0  1		Data processing - register
 *   0  1  1  1		Data processing - SIMD and floating point
 *   1  1  1  1		Data processing - SIMD and floating point
 * "-" means "don't care"
 */
enum aarch64_insn_encoding_class
{
    AARCH64_INSN_CLS_UNKNOWN, /* UNALLOCATED */
    AARCH64_INSN_CLS_DP_IMM, /* Data processing - immediate */
    AARCH64_INSN_CLS_DP_REG, /* Data processing - register */
    AARCH64_INSN_CLS_DP_FPSIMD, /* Data processing - SIMD and FP */
    AARCH64_INSN_CLS_LDST, /* Loads and stores */
    AARCH64_INSN_CLS_BR_SYS, /* Branch, exception generation and
					 * system instructions */
};

enum aarch64_insn_hint_op
{
    AARCH64_INSN_HINT_NOP = 0x0 << 5,
    AARCH64_INSN_HINT_YIELD = 0x1 << 5,
    AARCH64_INSN_HINT_WFE = 0x2 << 5,
    AARCH64_INSN_HINT_WFI = 0x3 << 5,
    AARCH64_INSN_HINT_SEV = 0x4 << 5,
    AARCH64_INSN_HINT_SEVL = 0x5 << 5,
};

enum aarch64_insn_imm_type
{
    AARCH64_INSN_IMM_ADR,
    AARCH64_INSN_IMM_26,
    AARCH64_INSN_IMM_19,
    AARCH64_INSN_IMM_16,
    AARCH64_INSN_IMM_14,
    AARCH64_INSN_IMM_12,
    AARCH64_INSN_IMM_9,
    AARCH64_INSN_IMM_7,
    AARCH64_INSN_IMM_6,
    AARCH64_INSN_IMM_S,
    AARCH64_INSN_IMM_R,
    AARCH64_INSN_IMM_MAX
};

enum aarch64_insn_register_type
{
    AARCH64_INSN_REGTYPE_RT,
    AARCH64_INSN_REGTYPE_RN,
    AARCH64_INSN_REGTYPE_RT2,
    AARCH64_INSN_REGTYPE_RM,
    AARCH64_INSN_REGTYPE_RD,
    AARCH64_INSN_REGTYPE_RA,
};

enum aarch64_insn_register
{
    AARCH64_INSN_REG_0 = 0,
    AARCH64_INSN_REG_1 = 1,
    AARCH64_INSN_REG_2 = 2,
    AARCH64_INSN_REG_3 = 3,
    AARCH64_INSN_REG_4 = 4,
    AARCH64_INSN_REG_5 = 5,
    AARCH64_INSN_REG_6 = 6,
    AARCH64_INSN_REG_7 = 7,
    AARCH64_INSN_REG_8 = 8,
    AARCH64_INSN_REG_9 = 9,
    AARCH64_INSN_REG_10 = 10,
    AARCH64_INSN_REG_11 = 11,
    AARCH64_INSN_REG_12 = 12,
    AARCH64_INSN_REG_13 = 13,
    AARCH64_INSN_REG_14 = 14,
    AARCH64_INSN_REG_15 = 15,
    AARCH64_INSN_REG_16 = 16,
    AARCH64_INSN_REG_17 = 17,
    AARCH64_INSN_REG_18 = 18,
    AARCH64_INSN_REG_19 = 19,
    AARCH64_INSN_REG_20 = 20,
    AARCH64_INSN_REG_21 = 21,
    AARCH64_INSN_REG_22 = 22,
    AARCH64_INSN_REG_23 = 23,
    AARCH64_INSN_REG_24 = 24,
    AARCH64_INSN_REG_25 = 25,
    AARCH64_INSN_REG_26 = 26,
    AARCH64_INSN_REG_27 = 27,
    AARCH64_INSN_REG_28 = 28,
    AARCH64_INSN_REG_29 = 29,
    AARCH64_INSN_REG_FP = 29, /* Frame pointer */
    AARCH64_INSN_REG_30 = 30,
    AARCH64_INSN_REG_LR = 30, /* Link register */
    AARCH64_INSN_REG_ZR = 31, /* Zero: as source register */
    AARCH64_INSN_REG_SP = 31 /* Stack pointer: as load/store base reg */
};

enum aarch64_insn_variant
{
    AARCH64_INSN_VARIANT_32BIT,
    AARCH64_INSN_VARIANT_64BIT
};

enum aarch64_insn_condition
{
    AARCH64_INSN_COND_EQ = 0x0, /* == */
    AARCH64_INSN_COND_NE = 0x1, /* != */
    AARCH64_INSN_COND_CS = 0x2, /* unsigned >= */
    AARCH64_INSN_COND_CC = 0x3, /* unsigned < */
    AARCH64_INSN_COND_MI = 0x4, /* < 0 */
    AARCH64_INSN_COND_PL = 0x5, /* >= 0 */
    AARCH64_INSN_COND_VS = 0x6, /* overflow */
    AARCH64_INSN_COND_VC = 0x7, /* no overflow */
    AARCH64_INSN_COND_HI = 0x8, /* unsigned > */
    AARCH64_INSN_COND_LS = 0x9, /* unsigned <= */
    AARCH64_INSN_COND_GE = 0xa, /* signed >= */
    AARCH64_INSN_COND_LT = 0xb, /* signed < */
    AARCH64_INSN_COND_GT = 0xc, /* signed > */
    AARCH64_INSN_COND_LE = 0xd, /* signed <= */
    AARCH64_INSN_COND_AL = 0xe, /* always */
};

enum aarch64_insn_branch_type
{
    AARCH64_INSN_BRANCH_NOLINK,
    AARCH64_INSN_BRANCH_LINK,
    AARCH64_INSN_BRANCH_RETURN,
    AARCH64_INSN_BRANCH_COMP_ZERO,
    AARCH64_INSN_BRANCH_COMP_NONZERO,
};

enum aarch64_insn_size_type
{
    AARCH64_INSN_SIZE_8,
    AARCH64_INSN_SIZE_16,
    AARCH64_INSN_SIZE_32,
    AARCH64_INSN_SIZE_64,
};

enum aarch64_insn_ldst_type
{
    AARCH64_INSN_LDST_LOAD_REG_OFFSET,
    AARCH64_INSN_LDST_STORE_REG_OFFSET,
    AARCH64_INSN_LDST_LOAD_PAIR_PRE_INDEX,
    AARCH64_INSN_LDST_STORE_PAIR_PRE_INDEX,
    AARCH64_INSN_LDST_LOAD_PAIR_POST_INDEX,
    AARCH64_INSN_LDST_STORE_PAIR_POST_INDEX,
};

enum aarch64_insn_adsb_type
{
    AARCH64_INSN_ADSB_ADD,
    AARCH64_INSN_ADSB_SUB,
    AARCH64_INSN_ADSB_ADD_SETFLAGS,
    AARCH64_INSN_ADSB_SUB_SETFLAGS
};

enum aarch64_insn_movewide_type
{
    AARCH64_INSN_MOVEWIDE_ZERO,
    AARCH64_INSN_MOVEWIDE_KEEP,
    AARCH64_INSN_MOVEWIDE_INVERSE
};

enum aarch64_insn_bitfield_type
{
    AARCH64_INSN_BITFIELD_MOVE,
    AARCH64_INSN_BITFIELD_MOVE_UNSIGNED,
    AARCH64_INSN_BITFIELD_MOVE_SIGNED
};

enum aarch64_insn_data1_type
{
    AARCH64_INSN_DATA1_REVERSE_16,
    AARCH64_INSN_DATA1_REVERSE_32,
    AARCH64_INSN_DATA1_REVERSE_64,
};

enum aarch64_insn_data2_type
{
    AARCH64_INSN_DATA2_UDIV,
    AARCH64_INSN_DATA2_SDIV,
    AARCH64_INSN_DATA2_LSLV,
    AARCH64_INSN_DATA2_LSRV,
    AARCH64_INSN_DATA2_ASRV,
    AARCH64_INSN_DATA2_RORV,
};

enum aarch64_insn_data3_type
{
    AARCH64_INSN_DATA3_MADD,
    AARCH64_INSN_DATA3_MSUB,
};

enum aarch64_insn_logic_type
{
    AARCH64_INSN_LOGIC_AND,
    AARCH64_INSN_LOGIC_BIC,
    AARCH64_INSN_LOGIC_ORR,
    AARCH64_INSN_LOGIC_ORN,
    AARCH64_INSN_LOGIC_EOR,
    AARCH64_INSN_LOGIC_EON,
    AARCH64_INSN_LOGIC_AND_SETFLAGS,
    AARCH64_INSN_LOGIC_BIC_SETFLAGS
};

#define AARCH64_INSN_IMM_MOVNZ AARCH64_INSN_IMM_MAX
#define AARCH64_INSN_IMM_MOVK AARCH64_INSN_IMM_16

#endif
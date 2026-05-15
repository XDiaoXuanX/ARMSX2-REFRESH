// SPDX-FileCopyrightText: 2002-2025 PCSX2 Dev Team
// SPDX-License-Identifier: GPL-3.0+

#pragma once
#ifdef __APPLE__

#include <string>
#include <vector>

#include "common/Pcsx2Types.h"

namespace DarwinMisc {
    extern int ARMSX2_CRASH_DIAG;
    extern int ARMSX2_REC_DIAG;
    extern int ARMSX2_FORCE_EE_INTERP;
    extern int ARMSX2_FORCE_JIT_VERIFY;
    extern int ARMSX2_CALL_TGT_X9;
    extern int ARMSX2_CRASH_PACK;
    extern int ARMSX2_WX_TRACE;
    extern int ARMSX2_CALLPROBE;
    extern int ARMSX2_JIT_HLE;
    extern int ARMSX2_FORCE_JIT;
    extern int ARMSX2_IOP_CORE_TYPE;


    struct IndirectEvent {
        u64 site;
        u64 target;
        u32 insn;
        u32 kind;
        u64 pad;
    };
    extern volatile IndirectEvent g_ie[8];
    extern volatile u32 g_ie_idx;

    struct WXTraceEvent {
        u64 tid;
        u64 caller;
        int write;
        int depth;
    };
    extern volatile WXTraceEvent g_wx_events[16];
    extern volatile u32 g_wx_idx;

    struct EmitEvent {
        u64 pc;
        u64 ptr;
        u64 sym;
        u64 tid;
        u64 caller;
    };
    extern volatile EmitEvent g_emit_events[32];
    extern volatile u32 g_emit_idx;

    extern volatile int g_jit_write_state;
    extern volatile int g_rec_stage;

    void SetCrashLogFD(int fd);

struct CPUClass {
	std::string name;
	u32 num_physical;
	u32 num_logical;
};


	void SetJitRange(void* base, size_t size);
	void SetLastGuestPC(u32 pc);
	void SetLastRecPtr(void* ptr);

    uintptr_t GetJitBase();
    uintptr_t GetJitEnd();
    u32 GetLastGuestPC();
    uintptr_t GetLastRecPtr();

	std::vector<CPUClass> GetCPUClasses();

    void LogDyldMain();

    void RecordJitBlock(u32 guest_pc, void* recptr, u32 size);
    bool FindJitBlock(uintptr_t site, u32* out_guest_pc, void** out_recptr);

    bool IsJITAvailable();

    enum class JitMode {
        Simulator,
        LuckTXM,
        LuckNoTXM,
        Legacy,
    };

    JitMode DetectJitMode();
    JitMode GetJitMode();

    extern ptrdiff_t g_code_rw_offset;
    extern uintptr_t g_code_rw_base;
    extern size_t    g_code_rw_size;

    void* MmapCodeDualMap(size_t size);

    void MunmapCodeDualMap(void* rx_ptr, size_t size);

    void LegacyEnsureExecutable();

}

#endif

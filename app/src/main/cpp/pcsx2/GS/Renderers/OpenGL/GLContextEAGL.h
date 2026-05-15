// SPDX-FileCopyrightText: 2002-2025 PCSX2 Dev Team
// SPDX-License-Identifier: LGPL-3.0+

#pragma once

#include "GS/Renderers/OpenGL/GLContext.h"

class GLContextEAGL final : public GLContext
{
public:
	GLContextEAGL(const WindowInfo& wi);
	~GLContextEAGL() override;

	static std::unique_ptr<GLContext> Create(const WindowInfo& wi, const Version* versions_to_try,
		size_t num_versions_to_try);

	void* GetProcAddress(const char* name) override;
	bool ChangeSurface(const WindowInfo& new_wi) override;
	void ResizeSurface(u32 new_surface_width = 0, u32 new_surface_height = 0) override;
	bool SwapBuffers() override;
	bool MakeCurrent() override;
	bool DoneCurrent() override;
	bool SetSwapInterval(s32 interval) override;
	std::unique_ptr<GLContext> CreateSharedContext(const WindowInfo& wi) override;
	u32 GetDefaultFramebuffer() const override;
	bool CreateSurface();

private:
	bool Initialize(const Version* versions_to_try, size_t num_versions_to_try, void* sharegroup);
	bool CreateContext(const Version& version, void* sharegroup);
	void DestroySurface();
	void DestroyContext();
	void ReleaseRetainedObject(void*& object);
	void RetainObject(void*& object, void* new_object);

	void* m_context = nullptr;
	void* m_layer = nullptr;
	u32 m_framebuffer = 0;
	u32 m_colorbuffer = 0;
};

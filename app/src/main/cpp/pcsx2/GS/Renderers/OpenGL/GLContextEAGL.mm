// SPDX-FileCopyrightText: 2002-2025 PCSX2 Dev Team
// SPDX-License-Identifier: LGPL-3.0+

#include "GS/Renderers/OpenGL/GLContextEAGL.h"

#include "common/Console.h"

#include "glad.h"

#import <Foundation/Foundation.h>
#import <OpenGLES/EAGL.h>
#import <OpenGLES/EAGLDrawable.h>
#import <QuartzCore/CAEAGLLayer.h>
#import <UIKit/UIKit.h>

#include <algorithm>
#include <dlfcn.h>

template <typename Fn>
static void RunOnMainThread(Fn&& fn)
{
	if ([NSThread isMainThread])
		fn();
	else
		dispatch_sync(dispatch_get_main_queue(), fn);
}

static EAGLContext* ToContext(void* context)
{
	return (__bridge EAGLContext*)context;
}

static CAEAGLLayer* ToLayer(void* layer)
{
	return (__bridge CAEAGLLayer*)layer;
}

GLContextEAGL::GLContextEAGL(const WindowInfo& wi)
	: GLContext(wi)
{
}

GLContextEAGL::~GLContextEAGL()
{
	DestroySurface();
	DestroyContext();
}

std::unique_ptr<GLContext> GLContextEAGL::Create(const WindowInfo& wi, const Version* versions_to_try,
	size_t num_versions_to_try)
{
	std::unique_ptr<GLContextEAGL> context = std::make_unique<GLContextEAGL>(wi);
	if (!context->Initialize(versions_to_try, num_versions_to_try, nullptr))
	{
		return nullptr;
	}

	return context;
}

bool GLContextEAGL::Initialize(const Version* versions_to_try, size_t num_versions_to_try, void* sharegroup)
{
	for (size_t i = 0; i < num_versions_to_try; i++)
	{
		if (versions_to_try[i].profile != Profile::ES)
			continue;

		if (CreateContext(versions_to_try[i], sharegroup))
		{
			const bool made_current = MakeCurrent();
			return made_current;
		}
	}

	return false;
}

bool GLContextEAGL::CreateContext(const Version& version, void* sharegroup)
{
	const EAGLRenderingAPI api = (version.major_version >= 3) ? kEAGLRenderingAPIOpenGLES3 : kEAGLRenderingAPIOpenGLES2;
	EAGLContext* context = nil;
	if (sharegroup)
		context = [[EAGLContext alloc] initWithAPI:api sharegroup:(__bridge EAGLSharegroup*)sharegroup];
	else
		context = [[EAGLContext alloc] initWithAPI:api];

	if (!context)
	{
		return false;
	}

	RetainObject(m_context, (__bridge void*)context);
	m_version = {Profile::ES, api == kEAGLRenderingAPIOpenGLES3 ? 3 : 2, 0};
	return true;
}

void* GLContextEAGL::GetProcAddress(const char* name)
{
	static void* s_gles = dlopen("/System/Library/Frameworks/OpenGLES.framework/OpenGLES", RTLD_LAZY | RTLD_LOCAL);
	if (s_gles)
	{
		if (void* proc = dlsym(s_gles, name))
			return proc;
	}

	return dlsym(RTLD_DEFAULT, name);
}

bool GLContextEAGL::ChangeSurface(const WindowInfo& new_wi)
{
	DestroySurface();
	m_wi = new_wi;
	return CreateSurface();
}

void GLContextEAGL::ResizeSurface(u32 new_surface_width, u32 new_surface_height)
{
	if (m_wi.type == WindowInfo::Type::Surfaceless)
		return;

	if (new_surface_width != 0 && new_surface_height != 0)
	{
		m_wi.surface_width = new_surface_width;
		m_wi.surface_height = new_surface_height;
	}

	DestroySurface();
	CreateSurface();
}

bool GLContextEAGL::SwapBuffers()
{
	if (!m_colorbuffer)
	{
		return true;
	}

	glBindRenderbuffer(GL_RENDERBUFFER, m_colorbuffer);
	const bool ok = [ToContext(m_context) presentRenderbuffer:GL_RENDERBUFFER];
	return ok;
}

bool GLContextEAGL::MakeCurrent()
{
	return [EAGLContext setCurrentContext:ToContext(m_context)];
}

bool GLContextEAGL::DoneCurrent()
{
	if ([EAGLContext currentContext] == ToContext(m_context))
		[EAGLContext setCurrentContext:nil];
	return true;
}

bool GLContextEAGL::SetSwapInterval(s32)
{
	return true;
}

std::unique_ptr<GLContext> GLContextEAGL::CreateSharedContext(const WindowInfo& wi)
{
	std::unique_ptr<GLContextEAGL> context = std::make_unique<GLContextEAGL>(wi);
	EAGLSharegroup* sharegroup = [ToContext(m_context) sharegroup];
	if (!context->Initialize(&m_version, 1, (__bridge void*)sharegroup))
		return nullptr;

	return context;
}

u32 GLContextEAGL::GetDefaultFramebuffer() const
{
	return m_framebuffer;
}

bool GLContextEAGL::CreateSurface()
{
	if (m_wi.type == WindowInfo::Type::Surfaceless)
	{
		return true;
	}

	if (m_wi.type != WindowInfo::Type::iOS || !m_wi.window_handle)
	{
		return false;
	}

	if (!MakeCurrent())
	{
		return false;
	}

	CAEAGLLayer* layer = nil;
	CAEAGLLayer** layer_out = &layer;
	UIView* resolved_view = nil;
	for (u32 attempt = 0; attempt < 20 && !layer; attempt++)
	{
		RunOnMainThread([&] {
			UIView* view = (__bridge UIView*)m_wi.window_handle;
			resolved_view = view;
			if (![view.layer isKindOfClass:[CAEAGLLayer class]])
			{
				if (attempt == 0)
				{
					Console.Error("EAGL: view=%p has layer=%s, expected CAEAGLLayer.", view,
						[NSStringFromClass([view.layer class]) UTF8String]);
				}
				return;
			}

			[view setNeedsLayout];
			[view layoutIfNeeded];
			if (!view.window || view.bounds.size.width <= 0.0 || view.bounds.size.height <= 0.0)
			{
				return;
			}

			CAEAGLLayer* view_layer = (CAEAGLLayer*)view.layer;
			view_layer.opaque = YES;
			view_layer.contentsScale = view.contentScaleFactor;
			view_layer.drawableProperties = @{
				kEAGLDrawablePropertyRetainedBacking: @NO,
				kEAGLDrawablePropertyColorFormat: kEAGLColorFormatRGBA8,
			};
			*layer_out = view_layer;
		});

		if (!layer)
			[NSThread sleepForTimeInterval:0.05];
	}

	if (!layer)
	{
		Console.Error("EAGL: game view is not ready for a drawable (view=%p).", resolved_view);
		return false;
	}

	RetainObject(m_layer, (__bridge void*)layer);

	glGenFramebuffers(1, &m_framebuffer);
	glBindFramebuffer(GL_FRAMEBUFFER, m_framebuffer);
	glGenRenderbuffers(1, &m_colorbuffer);
	glBindRenderbuffer(GL_RENDERBUFFER, m_colorbuffer);

	if (![ToContext(m_context) renderbufferStorage:GL_RENDERBUFFER fromDrawable:ToLayer(m_layer)])
	{
		Console.Error("EAGL: renderbufferStorage failed.");
		DestroySurface();
		return false;
	}

	glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, m_colorbuffer);

	GLint width = 0;
	GLint height = 0;
	glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &width);
	glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &height);
	m_wi.surface_width = static_cast<u32>(std::max<GLint>(width, 1));
	m_wi.surface_height = static_cast<u32>(std::max<GLint>(height, 1));

	if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
	{
		Console.Error("EAGL: framebuffer incomplete.");
		DestroySurface();
		return false;
	}

	return true;
}

void GLContextEAGL::DestroySurface()
{
	if (m_context && [EAGLContext currentContext] != ToContext(m_context))
		MakeCurrent();

	if (m_framebuffer)
	{
		glDeleteFramebuffers(1, &m_framebuffer);
		m_framebuffer = 0;
	}
	if (m_colorbuffer)
	{
		glDeleteRenderbuffers(1, &m_colorbuffer);
		m_colorbuffer = 0;
	}

	ReleaseRetainedObject(m_layer);
}

void GLContextEAGL::DestroyContext()
{
	DoneCurrent();
	ReleaseRetainedObject(m_context);
}

void GLContextEAGL::ReleaseRetainedObject(void*& object)
{
	if (!object)
		return;

	CFBridgingRelease(object);
	object = nullptr;
}

void GLContextEAGL::RetainObject(void*& object, void* new_object)
{
	ReleaseRetainedObject(object);
	if (new_object)
		object = (void*)CFBridgingRetain((__bridge id)new_object);
}

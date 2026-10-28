// Windows x64 UI Automation adapter for Minecraft's self-drawn, focused editor.
// No HWND is created. All window/caret operations run on SDL's window-owning thread.
// COM readers see a locked snapshot; ValuePattern writes are drained by the Java client thread.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <commctrl.h>
#include <ole2.h>
#include <uiautomation.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>

namespace {
// MinGW supplies the UIA interfaces but not all distributions ship its import library.
// Load only the Windows system copy and keep it loaded while COM references can exist.
decltype(&UiaHostProviderFromHwnd) hostProvider;
decltype(&UiaDisconnectProvider) disconnectProvider;
decltype(&UiaReturnRawElementProvider) returnProvider;
decltype(&UiaRaiseStructureChangedEvent) structureEvent;
decltype(&UiaRaiseAutomationEvent) automationEvent;
decltype(&UiaRaiseAutomationPropertyChangedEvent) propertyEvent;
template<typename T> bool loadFunction(HMODULE module, const char *name, T &target) {
    FARPROC address = GetProcAddress(module, name);
    static_assert(sizeof(address) == sizeof(target), "Windows function pointer ABI");
    std::memcpy(&target, &address, sizeof(target));
    return target != nullptr;
}
bool loadUia() {
    static HMODULE module = LoadLibraryExW(L"UIAutomationCore.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!module) return false;
#define LOAD_UIA(target, name) if (!loadFunction(module, name, target)) return false
    LOAD_UIA(hostProvider, "UiaHostProviderFromHwnd");
    LOAD_UIA(disconnectProvider, "UiaDisconnectProvider");
    LOAD_UIA(returnProvider, "UiaReturnRawElementProvider");
    LOAD_UIA(structureEvent, "UiaRaiseStructureChangedEvent");
    LOAD_UIA(automationEvent, "UiaRaiseAutomationEvent");
    LOAD_UIA(propertyEvent, "UiaRaiseAutomationPropertyChangedEvent");
#undef LOAD_UIA
    return true;
}
#define UiaHostProviderFromHwnd hostProvider
#define UiaDisconnectProvider disconnectProvider
#define UiaReturnRawElementProvider returnProvider
#define UiaRaiseStructureChangedEvent structureEvent
#define UiaRaiseAutomationEvent automationEvent
#define UiaRaiseAutomationPropertyChangedEvent propertyEvent

class Provider;
struct Context {
    SRWLOCK lock = SRWLOCK_INIT;
    HWND hwnd = nullptr;
    bool live = true;
    int token = 0, limit = 0, pendingToken = 0;
    UiaRect bounds{}, window{};
    std::wstring text, pending;
    Provider *root = nullptr, *edit = nullptr;
};
struct ReadLock {
    SRWLOCK *lock;
    explicit ReadLock(Context &c) : lock(&c.lock) { AcquireSRWLockShared(lock); }
    ~ReadLock() { ReleaseSRWLockShared(lock); }
};
struct WriteLock {
    SRWLOCK *lock;
    explicit WriteLock(Context &c) : lock(&c.lock) { AcquireSRWLockExclusive(lock); }
    ~WriteLock() { ReleaseSRWLockExclusive(lock); }
};

bool focused(HWND hwnd) { return GetForegroundWindow() == hwnd && !IsIconic(hwnd); }
bool inside(const UiaRect &r, double x, double y) {
    return x >= r.left && y >= r.top && x < r.left + r.width && y < r.top + r.height;
}
HRESULT boolean(VARIANT *out, bool value) {
    out->vt = VT_BOOL; out->boolVal = value ? VARIANT_TRUE : VARIANT_FALSE; return S_OK;
}
HRESULT string(VARIANT *out, const wchar_t *value) {
    out->vt = VT_BSTR; out->bstrVal = SysAllocString(value); return out->bstrVal ? S_OK : E_OUTOFMEMORY;
}
HRESULT rectangle(VARIANT *out, const UiaRect &r) {
    double values[] = {r.left, r.top, r.width, r.height};
    SAFEARRAY *array = SafeArrayCreateVector(VT_R8, 0, 4);
    if (!array) return E_OUTOFMEMORY;
    for (LONG i = 0; i < 4; ++i) SafeArrayPutElement(array, &i, &values[i]);
    out->vt = VT_ARRAY | VT_R8; out->parray = array; return S_OK;
}

class Provider final : public IRawElementProviderSimple, public IRawElementProviderFragment,
                       public IRawElementProviderFragmentRoot, public IValueProvider {
    std::atomic<ULONG> refs{1};
    IUnknown *marshaler = nullptr;
    bool valid() const { return context->live && (!id || (context->token == id && context->edit == this)); }
public:
    const std::shared_ptr<Context> context;
    const int id;
    Provider(std::shared_ptr<Context> c, int token) : context(std::move(c)), id(token) {
        CoCreateFreeThreadedMarshaler(static_cast<IRawElementProviderSimple *>(this), &marshaler);
    }
    ~Provider() { if (marshaler) marshaler->Release(); }
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID iid, void **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        if (iid == IID_IUnknown || iid == __uuidof(IRawElementProviderSimple))
            *out = static_cast<IRawElementProviderSimple *>(this);
        else if (iid == __uuidof(IRawElementProviderFragment)) *out = static_cast<IRawElementProviderFragment *>(this);
        else if (iid == __uuidof(IRawElementProviderFragmentRoot) && !id) *out = static_cast<IRawElementProviderFragmentRoot *>(this);
        else if (iid == __uuidof(IValueProvider) && id) *out = static_cast<IValueProvider *>(this);
        else if (iid == IID_IMarshal && marshaler) return marshaler->QueryInterface(iid, out);
        else return E_NOINTERFACE;
        AddRef(); return S_OK;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++refs; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG remaining = --refs;
        if (!remaining) delete this;
        return remaining;
    }
    HRESULT STDMETHODCALLTYPE get_ProviderOptions(ProviderOptions *out) override {
        if (!out) return E_POINTER;
        *out = static_cast<ProviderOptions>(ProviderOptions_ServerSideProvider | ProviderOptions_UseComThreading);
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE GetPatternProvider(PATTERNID pattern, IUnknown **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        if (id && pattern == UIA_ValuePatternId) { *out = static_cast<IValueProvider *>(this); AddRef(); }
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE GetPropertyValue(PROPERTYID property, VARIANT *out) override {
        if (!out) return E_POINTER;
        VariantInit(out);
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        switch (property) {
        case UIA_ControlTypePropertyId: out->vt = VT_I4; out->lVal = id ? UIA_EditControlTypeId : UIA_PaneControlTypeId; return S_OK;
        case UIA_HasKeyboardFocusPropertyId: return boolean(out, focused(context->hwnd) && (id || !context->edit));
        case UIA_IsKeyboardFocusablePropertyId: return boolean(out, true);
        case UIA_IsEnabledPropertyId: return boolean(out, IsWindowEnabled(context->hwnd));
        case UIA_IsOffscreenPropertyId: return boolean(out, !IsWindowVisible(context->hwnd) || IsIconic(context->hwnd));
        case UIA_IsPasswordPropertyId: return boolean(out, false);
        case UIA_IsControlElementPropertyId:
        case UIA_IsContentElementPropertyId: return boolean(out, true);
        case UIA_IsValuePatternAvailablePropertyId: return boolean(out, id != 0);
        case UIA_ValueIsReadOnlyPropertyId: return boolean(out, id == 0);
        case UIA_ValueValuePropertyId: return id ? string(out, context->text.c_str()) : S_OK;
        case UIA_NamePropertyId: return string(out, id ? L"Minecraft text input" : L"Minecraft");
        case UIA_FrameworkIdPropertyId: return string(out, L"GTNH Easy Touch");
        case UIA_ClassNamePropertyId: return id ? string(out, L"MinecraftTextField") : S_OK;
        case UIA_BoundingRectanglePropertyId: return rectangle(out, id ? context->bounds : context->window);
        default: return S_OK; // VT_EMPTY lets the host supply other window properties.
        }
    }
    HRESULT STDMETHODCALLTYPE get_HostRawElementProvider(IRawElementProviderSimple **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        HWND hwnd;
        { ReadLock lock(*context); if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE; hwnd = context->hwnd; }
        return id ? S_OK : UiaHostProviderFromHwnd(hwnd, out);
    }
    HRESULT STDMETHODCALLTYPE Navigate(NavigateDirection direction, IRawElementProviderFragment **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        Provider *next = nullptr;
        if (id && direction == NavigateDirection_Parent) next = context->root;
        if (!id && (direction == NavigateDirection_FirstChild || direction == NavigateDirection_LastChild)) next = context->edit;
        if (next) { *out = static_cast<IRawElementProviderFragment *>(next); next->AddRef(); }
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE GetRuntimeId(SAFEARRAY **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        if (!id) return S_OK; // HWND supplies the root's runtime ID.
        *out = SafeArrayCreateVector(VT_I4, 0, 2);
        if (!*out) return E_OUTOFMEMORY;
        LONG index = 0, value = UiaAppendRuntimeId;
        SafeArrayPutElement(*out, &index, &value);
        index = 1; value = id;
        return SafeArrayPutElement(*out, &index, &value);
    }
    HRESULT STDMETHODCALLTYPE get_BoundingRectangle(UiaRect *out) override {
        if (!out) return E_POINTER;
        *out = {};
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        *out = id ? context->bounds : context->window; return S_OK;
    }
    HRESULT STDMETHODCALLTYPE GetEmbeddedFragmentRoots(SAFEARRAY **out) override {
        if (!out) return E_POINTER;
        *out = nullptr; return S_OK;
    }
    HRESULT STDMETHODCALLTYPE SetFocus() override {
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        // Only the actual current editor is exposed. Never steal focus from another application.
        return focused(context->hwnd) ? S_OK : UIA_E_INVALIDOPERATION;
    }
    HRESULT STDMETHODCALLTYPE get_FragmentRoot(IRawElementProviderFragmentRoot **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        *out = static_cast<IRawElementProviderFragmentRoot *>(context->root); context->root->AddRef(); return S_OK;
    }
    HRESULT STDMETHODCALLTYPE ElementProviderFromPoint(double x, double y, IRawElementProviderFragment **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        Provider *next = context->edit && inside(context->bounds, x, y) ? context->edit : context->root;
        if (inside(context->window, x, y)) { *out = static_cast<IRawElementProviderFragment *>(next); next->AddRef(); }
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE GetFocus(IRawElementProviderFragment **out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        if (focused(context->hwnd)) {
            Provider *next = context->edit ? context->edit : context->root;
            *out = static_cast<IRawElementProviderFragment *>(next); next->AddRef();
        }
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE SetValue(LPCWSTR value) override {
        if (!value) return E_INVALIDARG;
        size_t length = wcsnlen(value, 32768);
        if (length > 32767) return E_INVALIDARG;
        try {
            WriteLock lock(*context);
            if (!valid() || !id || !focused(context->hwnd)) return UIA_E_ELEMENTNOTAVAILABLE;
            if (length > static_cast<size_t>(context->limit)) return E_INVALIDARG;
            context->pending.assign(value, length);
            context->pendingToken = id;
            return S_OK;
        } catch (...) { return E_OUTOFMEMORY; }
    }
    HRESULT STDMETHODCALLTYPE get_Value(BSTR *out) override {
        if (!out) return E_POINTER;
        *out = nullptr;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        *out = SysAllocStringLen(context->text.data(), static_cast<UINT>(context->text.size()));
        return *out ? S_OK : E_OUTOFMEMORY;
    }
    HRESULT STDMETHODCALLTYPE get_IsReadOnly(BOOL *out) override {
        if (!out) return E_POINTER;
        ReadLock lock(*context);
        if (!valid()) return UIA_E_ELEMENTNOTAVAILABLE;
        *out = id ? FALSE : TRUE; return S_OK;
    }
};

// Accessed on the window thread except et_uia_take, which is serialized with updates by Java.
Provider *root = nullptr;
DWORD owner = 0;
bool comOwned = false, caretOwned = false;
int caretWidth = 0, caretHeight = 0;
constexpr UINT_PTR subclassId = 0x45545549;

void releaseCaret() {
    if (caretOwned) DestroyCaret();
    caretOwned = false; caretWidth = caretHeight = 0;
}
void disconnect(Provider *provider) {
    if (provider) { UiaDisconnectProvider(provider); provider->Release(); }
}
void retire() {
    if (!root) return;
    auto context = root->context;
    Provider *previous;
    {
        WriteLock lock(*context);
        context->live = false;
        context->token = context->pendingToken = 0;
        previous = context->edit;
        context->edit = context->root = nullptr;
        context->text.clear(); context->pending.clear();
    }
    releaseCaret();
    disconnect(previous);
    Provider *old = root; root = nullptr;
    disconnect(old);
}
LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wparam, LPARAM lparam, UINT_PTR, DWORD_PTR) {
    if (root && message == WM_GETOBJECT && static_cast<LONG>(lparam) == UiaRootObjectId)
        return UiaReturnRawElementProvider(hwnd, wparam, lparam, static_cast<IRawElementProviderSimple *>(root));
    if (message == WM_KILLFOCUS) releaseCaret();
    if (message == WM_NCDESTROY) {
        RemoveWindowSubclass(hwnd, windowProc, subclassId);
        retire();
    }
    return DefSubclassProc(hwnd, message, wparam, lparam);
}

struct Packet {
    int32_t token, limit, length, reserved;
    double left, top, right, bottom, caretX, caretY, caretWidth, caretHeight;
};
static_assert(sizeof(Packet) == 80, "Java/C ABI layout");

int update(const Packet &packet, const wchar_t *text) {
    if (!root || GetCurrentThreadId() != owner) return 1;
    if (packet.token < 0 || packet.length < 0 || packet.length > 1048576 || !text) return 2;
    auto context = root->context;
    RECT client{};
    POINT origin{};
    if (!GetClientRect(context->hwnd, &client) || !ClientToScreen(context->hwnd, &origin)) return 3;
    UiaRect window{static_cast<double>(origin.x), static_cast<double>(origin.y),
        static_cast<double>(client.right), static_cast<double>(client.bottom)};
    int token = focused(context->hwnd) ? packet.token : 0;
    UiaRect bounds{};
    if (token) {
        const double *values = &packet.left;
        for (int i = 0; i < 8; ++i) if (!std::isfinite(values[i])) return 4;
        double left = std::clamp(packet.left, 0.0, 1.0), top = std::clamp(packet.top, 0.0, 1.0);
        double right = std::clamp(packet.right, 0.0, 1.0), bottom = std::clamp(packet.bottom, 0.0, 1.0);
        bounds = {origin.x + left * client.right, origin.y + top * client.bottom,
            (right - left) * client.right, (bottom - top) * client.bottom};
        if (bounds.width < 1 || bounds.height < 1) token = 0;
    }
    Provider *previous = nullptr, *current = nullptr;
    bool focusChanged, valueChanged;
    std::wstring before;
    {
        WriteLock lock(*context);
        focusChanged = context->token != token;
        valueChanged = token && context->text != std::wstring(text, packet.length);
        if (valueChanged) before = context->text;
        if (focusChanged) {
            current = token ? new Provider(context, token) : nullptr;
            previous = context->edit;
            context->edit = current;
            context->pendingToken = 0; context->pending.clear();
        } else current = context->edit;
        context->token = token;
        context->limit = std::clamp(packet.limit, 0, 32767);
        context->bounds = bounds; context->window = window;
        context->text.assign(token ? text : L"", token ? packet.length : 0);
    }
    if (token) {
        // A hidden Win32 caret exposes the real game caret to Sunshine's GetGUIThreadInfo fallback.
        // SDL renders/handles text itself and does not own a Win32 caret in this backend.
        int w = std::clamp(static_cast<int>(std::ceil(packet.caretWidth * client.right)), 1, static_cast<int>(bounds.width));
        int h = std::clamp(static_cast<int>(std::ceil(packet.caretHeight * client.bottom)), 1, static_cast<int>(bounds.height));
        if (!caretOwned || w != caretWidth || h != caretHeight) {
            releaseCaret();
            caretOwned = CreateCaret(context->hwnd, nullptr, w, h) != FALSE;
            caretWidth = w; caretHeight = h; // CreateCaret is hidden until ShowCaret; never draw a second cursor.
        }
        if (caretOwned) SetCaretPos(
            static_cast<int>(std::clamp(origin.x + packet.caretX * client.right, bounds.left, bounds.left + bounds.width - w) - origin.x),
            static_cast<int>(std::clamp(origin.y + packet.caretY * client.bottom, bounds.top, bounds.top + bounds.height - h) - origin.y));
    } else releaseCaret();
    // Never call UIA or notify external clients while holding the snapshot lock.
    if (focusChanged) {
        UiaRaiseStructureChangedEvent(root, StructureChangeType_ChildrenInvalidated, nullptr, 0);
        if (focused(context->hwnd)) {
            UiaRaiseAutomationEvent(current ? current : root, UIA_AutomationFocusChangedEventId);
            // Foundation's observer listens to WinEvents, not just UIA events.
            NotifyWinEvent(EVENT_OBJECT_FOCUS, context->hwnd, OBJID_CLIENT, CHILDID_SELF);
        }
    }
    if (valueChanged && current && !focusChanged) {
        VARIANT oldValue{}, newValue{};
        string(&oldValue, before.c_str()); string(&newValue, text);
        UiaRaiseAutomationPropertyChangedEvent(current, UIA_ValueValuePropertyId, oldValue, newValue);
        VariantClear(&oldValue); VariantClear(&newValue);
    }
    disconnect(previous);
    return 0;
}
} // namespace

#define EXPORT extern "C" __declspec(dllexport)
EXPORT int et_uia_version() { return 1; }
EXPORT int et_uia_install(HWND hwnd) {
    if (!IsWindow(hwnd) || GetWindowThreadProcessId(hwnd, nullptr) != GetCurrentThreadId()) return 1;
    if (root) return root->context->hwnd == hwnd ? 0 : 2;
    if (!loadUia()) return 8;
    try {
        if (!owner) {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            if (FAILED(hr) && hr != RPC_E_CHANGED_MODE) return 3;
            comOwned = SUCCEEDED(hr);
            owner = GetCurrentThreadId();
        }
        if (owner != GetCurrentThreadId()) return 4;
        auto context = std::make_shared<Context>();
        context->hwnd = hwnd;
        root = new Provider(context, 0); context->root = root;
        if (!SetWindowSubclass(hwnd, windowProc, subclassId, 0)) { retire(); return 5; }
        return 0;
    } catch (...) { retire(); return 6; }
}
EXPORT int et_uia_update(const Packet *packet, const wchar_t *text) {
    if (!packet) return 2;
    try { return update(*packet, text); }
    catch (...) { return 7; }
}
EXPORT int et_uia_take(void *buffer, int capacity) {
    if (!root || !buffer || capacity < 8) return -1;
    auto context = root->context;
    WriteLock lock(*context);
    if (!context->pendingToken) return -1;
    int length = static_cast<int>(context->pending.size());
    if (capacity < 8 + length * 2) return -1;
    auto *header = static_cast<int32_t *>(buffer);
    header[0] = context->pendingToken; header[1] = length;
    std::memcpy(header + 2, context->pending.data(), length * 2);
    context->pendingToken = 0; context->pending.clear();
    return length;
}
EXPORT int et_uia_detach() {
    if (owner && owner != GetCurrentThreadId()) return 1;
    if (root) {
        HWND hwnd = root->context->hwnd;
        RemoveWindowSubclass(hwnd, windowProc, subclassId);
        retire();
        if (focused(hwnd)) NotifyWinEvent(EVENT_OBJECT_FOCUS, hwnd, OBJID_CLIENT, CHILDID_SELF);
    }
    if (comOwned) { CoUninitialize(); comOwned = false; }
    owner = 0;
    return 0;
}

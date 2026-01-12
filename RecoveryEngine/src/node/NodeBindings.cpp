/**
 * @file NodeBindings.cpp
 * @brief Node-API bindings for the Recovery Engine
 * 
 * Exposes the C++ recovery engine to Node.js/Electron applications
 * through the Node-API (N-API) interface.
 */

#include <napi.h>
#include "RecoveryEngine.h"
#include "RecoveryTypes.h"
#include "AsyncWorkers.h"
#include "TypeConverters.h"

#include <memory>
#include <map>
#include <mutex>

namespace RecoveryEngine {
namespace NodeBindings {

// Global engine instance
static std::shared_ptr<DataRecoveryEngine> g_engine;
static std::mutex g_engineMutex;

// Session storage
static std::map<std::string, std::shared_ptr<RecoverySession>> g_sessions;
static std::mutex g_sessionsMutex;

/**
 * @brief Initialize the recovery engine
 * 
 * @param info Arguments: [databasePath: string (optional)]
 * @return Object with version info
 */
Napi::Value Initialize(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::string dbPath;
    if (info.Length() > 0 && info[0].IsString()) {
        dbPath = info[0].As<Napi::String>().Utf8Value();
    }
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    
    try {
        g_engine = DataRecoveryEngine::create(dbPath);
        
        Napi::Object result = Napi::Object::New(env);
        result.Set("version", Napi::String::New(env, g_engine->getVersion()));
        result.Set("tskVersion", Napi::String::New(env, g_engine->getTSKVersion()));
        result.Set("initialized", Napi::Boolean::New(env, true));
        
        return result;
    } catch (const std::exception& e) {
        Napi::Error::New(env, e.what()).ThrowAsJavaScriptException();
        return env.Undefined();
    }
}

/**
 * @brief Get engine version
 */
Napi::Value GetVersion(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        return Napi::String::New(env, "Not initialized");
    }
    
    return Napi::String::New(env, g_engine->getVersion());
}

/**
 * @brief Scan for connected storage devices (async)
 * 
 * Returns a Promise that resolves to an array of DeviceInfo objects
 */
Napi::Value ScanDevices(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new ScanDevicesWorker(env, deferred, g_engine);
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Get information about a specific device
 * 
 * @param info Arguments: [devicePath: string]
 * @return Promise<DeviceInfo>
 */
Napi::Value GetDeviceInfo(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Device path required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string devicePath = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new GetDeviceInfoWorker(env, deferred, g_engine, devicePath);
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Scan for volumes/partitions on a device
 * 
 * @param info Arguments: [devicePath: string]
 * @return Promise<VolumeInfo[]>
 */
Napi::Value ScanVolumes(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Device path required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string devicePath = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new ScanVolumesWorker(env, deferred, g_engine, devicePath);
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Create a new recovery session
 * 
 * @param info Arguments: [sessionName: string (optional)]
 * @return Session ID string
 */
Napi::Value CreateSession(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::string sessionName;
    if (info.Length() > 0 && info[0].IsString()) {
        sessionName = info[0].As<Napi::String>().Utf8Value();
    }
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    try {
        auto session = g_engine->createSession(sessionName);
        std::string sessionId = session->getSessionId();
        
        {
            std::lock_guard<std::mutex> sessLock(g_sessionsMutex);
            g_sessions[sessionId] = session;
        }
        
        Napi::Object result = Napi::Object::New(env);
        result.Set("sessionId", Napi::String::New(env, sessionId));
        result.Set("sessionName", Napi::String::New(env, session->getSessionName()));
        
        return result;
    } catch (const std::exception& e) {
        Napi::Error::New(env, e.what()).ThrowAsJavaScriptException();
        return env.Undefined();
    }
}

/**
 * @brief Start scanning a volume (async with streaming results)
 * 
 * @param info Arguments: [sessionId: string, volumePath: string, options: object, 
 *                        onProgress: function, onFile: function]
 * @return Promise<ScanResult>
 */
Napi::Value StartScan(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 2) {
        Napi::TypeError::New(env, "Session ID and volume path required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    std::string volumePath = info[1].As<Napi::String>().Utf8Value();
    
    ScanOptions options;
    if (info.Length() > 2 && info[2].IsObject()) {
        options = TypeConverters::toScanOptions(info[2].As<Napi::Object>());
    }
    
    Napi::Function progressCallback;
    Napi::Function fileCallback;
    
    if (info.Length() > 3 && info[3].IsFunction()) {
        progressCallback = info[3].As<Napi::Function>();
    }
    if (info.Length() > 4 && info[4].IsFunction()) {
        fileCallback = info[4].As<Napi::Function>();
    }
    
    // Get session
    std::shared_ptr<RecoverySession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessionsMutex);
        auto it = g_sessions.find(sessionId);
        if (it == g_sessions.end()) {
            Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
            return env.Undefined();
        }
        session = it->second;
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new ScanVolumeWorker(
        env, deferred, session, volumePath, options,
        progressCallback, fileCallback
    );
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Pause scan operation
 */
Napi::Value PauseScan(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    it->second->pauseScan();
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Resume scan operation
 */
Napi::Value ResumeScan(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    it->second->resumeScan();
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Cancel scan operation
 */
Napi::Value CancelScan(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    it->second->cancelScan();
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Get scan progress
 */
Napi::Value GetScanProgress(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    ScanProgress progress = it->second->getScanProgress();
    return TypeConverters::toJsObject(env, progress);
}

/**
 * @brief List files from session (paginated)
 * 
 * @param info Arguments: [sessionId: string, options: {offset, limit, category, extension}]
 * @return Promise<FileMetadata[]>
 */
Napi::Value ListFiles(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    size_t offset = 0;
    size_t limit = 0;
    
    if (info.Length() > 1 && info[1].IsObject()) {
        Napi::Object opts = info[1].As<Napi::Object>();
        if (opts.Has("offset")) {
            offset = opts.Get("offset").As<Napi::Number>().Uint32Value();
        }
        if (opts.Has("limit")) {
            limit = opts.Get("limit").As<Napi::Number>().Uint32Value();
        }
    }
    
    std::shared_ptr<RecoverySession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessionsMutex);
        auto it = g_sessions.find(sessionId);
        if (it == g_sessions.end()) {
            Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
            return env.Undefined();
        }
        session = it->second;
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new ListFilesWorker(env, deferred, session, offset, limit);
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Get file counts by category
 */
Napi::Value GetFileCounts(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = it->second->getFileCounts();
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    Napi::Object counts = Napi::Object::New(env);
    for (const auto& pair : result.value) {
        counts.Set(
            fileCategoryToString(pair.first),
            Napi::Number::New(env, pair.second)
        );
    }
    
    counts.Set("total", Napi::Number::New(env, it->second->getTotalFileCount()));
    counts.Set("deleted", Napi::Number::New(env, it->second->getDeletedFileCount()));
    
    return counts;
}

/**
 * @brief Preview file content
 * 
 * @param info Arguments: [volumePath: string, fileId: number, maxBytes: number]
 * @return Promise<Buffer>
 */
Napi::Value PreviewFile(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 2) {
        Napi::TypeError::New(env, "Volume path and file ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string volumePath = info[0].As<Napi::String>().Utf8Value();
    uint64_t fileId = info[1].As<Napi::Number>().Int64Value();
    size_t maxBytes = 1024 * 1024; // Default 1MB
    
    if (info.Length() > 2 && info[2].IsNumber()) {
        maxBytes = info[2].As<Napi::Number>().Uint32Value();
    }
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new PreviewFileWorker(env, deferred, g_engine, volumePath, fileId, maxBytes);
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Recover files (async)
 * 
 * @param info Arguments: [sessionId: string, fileIds: number[], options: RecoveryOptions, 
 *                        onProgress: function]
 * @return Promise<RecoveryProgress>
 */
Napi::Value RecoverFiles(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 3) {
        Napi::TypeError::New(env, "Session ID, file IDs, and options required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    // Parse file IDs array
    Napi::Array fileIdsArray = info[1].As<Napi::Array>();
    std::vector<uint64_t> fileIds;
    for (uint32_t i = 0; i < fileIdsArray.Length(); ++i) {
        fileIds.push_back(fileIdsArray.Get(i).As<Napi::Number>().Int64Value());
    }
    
    RecoveryOptions options = TypeConverters::toRecoveryOptions(info[2].As<Napi::Object>());
    
    Napi::Function progressCallback;
    if (info.Length() > 3 && info[3].IsFunction()) {
        progressCallback = info[3].As<Napi::Function>();
    }
    
    std::shared_ptr<RecoverySession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessionsMutex);
        auto it = g_sessions.find(sessionId);
        if (it == g_sessions.end()) {
            Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
            return env.Undefined();
        }
        session = it->second;
    }
    
    auto deferred = Napi::Promise::Deferred::New(env);
    auto worker = new RecoverFilesWorker(
        env, deferred, session, fileIds, options, progressCallback
    );
    worker->Queue();
    
    return deferred.Promise();
}

/**
 * @brief Get metadata for a file
 */
Napi::Value GetMetadata(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 2) {
        Napi::TypeError::New(env, "Volume path and file ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string volumePath = info[0].As<Napi::String>().Utf8Value();
    uint64_t fileId = info[1].As<Napi::Number>().Int64Value();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = g_engine->getMetadata(volumePath, fileId);
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    return TypeConverters::toJsObject(env, result.value);
}

/**
 * @brief Get recovery score for a file
 */
Napi::Value GetRecoveryScore(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 2) {
        Napi::TypeError::New(env, "Volume path and file ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string volumePath = info[0].As<Napi::String>().Utf8Value();
    uint64_t fileId = info[1].As<Napi::Number>().Int64Value();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = g_engine->getRecoveryScore(volumePath, fileId);
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    Napi::Object score = Napi::Object::New(env);
    score.Set("score", Napi::String::New(env, recoveryScoreToString(result.value.first)));
    score.Set("confidence", Napi::Number::New(env, result.value.second));
    
    return score;
}

/**
 * @brief Save session to database
 */
Napi::Value SaveSession(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) {
        Napi::Error::New(env, "Session not found").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = it->second->save();
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    return Napi::Boolean::New(env, true);
}

/**
 * @brief List saved sessions
 */
Napi::Value ListSessions(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = g_engine->listSessions();
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    Napi::Array sessions = Napi::Array::New(env, result.value.size());
    for (size_t i = 0; i < result.value.size(); ++i) {
        sessions.Set(i, TypeConverters::toJsObject(env, result.value[i]));
    }
    
    return sessions;
}

/**
 * @brief Delete a session
 */
Napi::Value DeleteSession(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    if (info.Length() < 1 || !info[0].IsString()) {
        Napi::TypeError::New(env, "Session ID required").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    std::string sessionId = info[0].As<Napi::String>().Utf8Value();
    
    {
        std::lock_guard<std::mutex> lock(g_sessionsMutex);
        g_sessions.erase(sessionId);
    }
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (!g_engine) {
        Napi::Error::New(env, "Engine not initialized").ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    auto result = g_engine->deleteSession(sessionId);
    if (result.isError()) {
        Napi::Error::New(env, result.errorMessage).ThrowAsJavaScriptException();
        return env.Undefined();
    }
    
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Cancel all operations
 */
Napi::Value CancelAll(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    std::lock_guard<std::mutex> lock(g_engineMutex);
    if (g_engine) {
        g_engine->cancelAll();
    }
    
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Shutdown and cleanup
 */
Napi::Value Shutdown(const Napi::CallbackInfo& info) {
    Napi::Env env = info.Env();
    
    {
        std::lock_guard<std::mutex> lock(g_sessionsMutex);
        g_sessions.clear();
    }
    
    {
        std::lock_guard<std::mutex> lock(g_engineMutex);
        g_engine.reset();
    }
    
    return Napi::Boolean::New(env, true);
}

/**
 * @brief Module initialization
 */
Napi::Object Init(Napi::Env env, Napi::Object exports) {
    // Core functions
    exports.Set("initialize", Napi::Function::New(env, Initialize));
    exports.Set("getVersion", Napi::Function::New(env, GetVersion));
    exports.Set("shutdown", Napi::Function::New(env, Shutdown));
    
    // Device discovery
    exports.Set("scanDevices", Napi::Function::New(env, ScanDevices));
    exports.Set("getDeviceInfo", Napi::Function::New(env, GetDeviceInfo));
    exports.Set("scanVolumes", Napi::Function::New(env, ScanVolumes));
    
    // Session management
    exports.Set("createSession", Napi::Function::New(env, CreateSession));
    exports.Set("listSessions", Napi::Function::New(env, ListSessions));
    exports.Set("saveSession", Napi::Function::New(env, SaveSession));
    exports.Set("deleteSession", Napi::Function::New(env, DeleteSession));
    
    // Scanning
    exports.Set("startScan", Napi::Function::New(env, StartScan));
    exports.Set("pauseScan", Napi::Function::New(env, PauseScan));
    exports.Set("resumeScan", Napi::Function::New(env, ResumeScan));
    exports.Set("cancelScan", Napi::Function::New(env, CancelScan));
    exports.Set("getScanProgress", Napi::Function::New(env, GetScanProgress));
    
    // File operations
    exports.Set("listFiles", Napi::Function::New(env, ListFiles));
    exports.Set("getFileCounts", Napi::Function::New(env, GetFileCounts));
    exports.Set("previewFile", Napi::Function::New(env, PreviewFile));
    exports.Set("getMetadata", Napi::Function::New(env, GetMetadata));
    exports.Set("getRecoveryScore", Napi::Function::New(env, GetRecoveryScore));
    
    // Recovery
    exports.Set("recoverFiles", Napi::Function::New(env, RecoverFiles));
    
    // Global operations
    exports.Set("cancelAll", Napi::Function::New(env, CancelAll));
    
    // Constants
    Napi::Object constants = Napi::Object::New(env);
    
    // Scan types
    Napi::Object scanTypes = Napi::Object::New(env);
    scanTypes.Set("QuickScan", Napi::Number::New(env, static_cast<int>(ScanType::QuickScan)));
    scanTypes.Set("DeepScan", Napi::Number::New(env, static_cast<int>(ScanType::DeepScan)));
    scanTypes.Set("PartitionScan", Napi::Number::New(env, static_cast<int>(ScanType::PartitionScan)));
    constants.Set("ScanType", scanTypes);
    
    // File categories
    Napi::Object categories = Napi::Object::New(env);
    categories.Set("Photo", Napi::Number::New(env, static_cast<int>(FileCategory::Photo)));
    categories.Set("Video", Napi::Number::New(env, static_cast<int>(FileCategory::Video)));
    categories.Set("Audio", Napi::Number::New(env, static_cast<int>(FileCategory::Audio)));
    categories.Set("Document", Napi::Number::New(env, static_cast<int>(FileCategory::Document)));
    categories.Set("Archive", Napi::Number::New(env, static_cast<int>(FileCategory::Archive)));
    categories.Set("Email", Napi::Number::New(env, static_cast<int>(FileCategory::Email)));
    categories.Set("Database", Napi::Number::New(env, static_cast<int>(FileCategory::Database)));
    categories.Set("Executable", Napi::Number::New(env, static_cast<int>(FileCategory::Executable)));
    categories.Set("System", Napi::Number::New(env, static_cast<int>(FileCategory::System)));
    categories.Set("Other", Napi::Number::New(env, static_cast<int>(FileCategory::Other)));
    constants.Set("FileCategory", categories);
    
    // Recovery scores
    Napi::Object scores = Napi::Object::New(env);
    scores.Set("Excellent", Napi::String::New(env, "Excellent"));
    scores.Set("Good", Napi::String::New(env, "Good"));
    scores.Set("Partial", Napi::String::New(env, "Partial"));
    scores.Set("Poor", Napi::String::New(env, "Poor"));
    scores.Set("Unrecoverable", Napi::String::New(env, "Unrecoverable"));
    constants.Set("RecoveryScore", scores);
    
    exports.Set("constants", constants);
    
    return exports;
}

NODE_API_MODULE(recovery_engine, Init)

} // namespace NodeBindings
} // namespace RecoveryEngine

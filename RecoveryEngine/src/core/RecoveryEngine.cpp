/**
 * @file RecoveryEngine.cpp
 * @brief Main recovery engine implementation
 */

#include "RecoveryEngine.h"
#include "DeviceScanner.h"
#include "VolumeScanner.h"
#include "FileSystemParser.h"
#include "DeepScanner.h"
#include "FileCarver.h"
#include "SessionDatabase.h"
#include "ProgressTracker.h"
#include "DiskAccess.h"

#include <tsk/libtsk.h>
#include <mutex>
#include <atomic>
#include <thread>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <random>

namespace fs = std::filesystem;

namespace RecoveryEngine {

// Version info
static const char* ENGINE_VERSION = "1.0.0";

/**
 * @brief Generate unique session ID
 */
static std::string generateSessionId() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<uint64_t> dis;
    
    uint64_t id = dis(gen);
    std::stringstream ss;
    ss << std::hex << std::setfill('0') << std::setw(16) << id;
    return ss.str();
}

/**
 * @brief Get default database path
 */
static std::string getDefaultDatabasePath() {
    fs::path tempDir = fs::temp_directory_path();
    fs::path dbPath = tempDir / "RecoveryEngine" / "sessions.db";
    fs::create_directories(dbPath.parent_path());
    return dbPath.string();
}

// ============================================================================
// RecoverySessionImpl - Implementation of RecoverySession interface
// ============================================================================

class RecoverySessionImpl : public RecoverySession {
public:
    RecoverySessionImpl(
        const std::string& sessionId,
        const std::string& sessionName,
        std::shared_ptr<SessionDatabase> database
    ) : m_sessionId(sessionId)
      , m_sessionName(sessionName)
      , m_database(database)
    {
        m_progressTracker = std::make_unique<ProgressTracker>();
        m_recoveryTracker = std::make_unique<RecoveryProgressTracker>();
    }
    
    ~RecoverySessionImpl() override {
        cancelScan();
    }
    
    std::string getSessionId() const override {
        return m_sessionId;
    }
    
    std::string getSessionName() const override {
        return m_sessionName;
    }
    
    SessionState getState() const override {
        SessionState state;
        state.sessionId = m_sessionId;
        state.sessionName = m_sessionName;
        state.devicePath = m_devicePath;
        state.scanProgress = m_progressTracker->getProgress();
        state.isComplete = (state.scanProgress.status == OperationStatus::Completed);
        return state;
    }
    
    Result<void> scanVolume(
        const std::string& volumePath,
        const ScanOptions& options,
        ProgressCallback progressCallback,
        FileFoundCallback fileCallback
    ) override {
        std::lock_guard<std::mutex> lock(m_scanMutex);
        
        if (m_progressTracker->getStatus() == OperationStatus::Running) {
            return Result<void>::failure(
                RecoveryError::DeviceBusy,
                "Scan already in progress"
            );
        }
        
        m_devicePath = volumePath;
        m_scanOptions = options;
        
        // Set up progress tracking
        m_progressTracker->setCallback(progressCallback);
        m_progressTracker->setStatus(OperationStatus::Running);
        
        // Perform the appropriate scan type
        if (options.scanType == ScanType::DeepScan) {
            return performDeepScan(volumePath, options, fileCallback);
        } else {
            return performQuickScan(volumePath, options, fileCallback);
        }
    }
    
    Result<void> pauseScan() override {
        m_progressTracker->requestPause();
        m_progressTracker->setStatus(OperationStatus::Paused);
        return Result<void>::success();
    }
    
    Result<void> resumeScan() override {
        m_progressTracker->requestResume();
        m_progressTracker->setStatus(OperationStatus::Running);
        return Result<void>::success();
    }
    
    Result<void> cancelScan() override {
        m_progressTracker->requestCancel();
        m_progressTracker->setStatus(OperationStatus::Cancelled);
        return Result<void>::success();
    }
    
    ScanProgress getScanProgress() const override {
        return m_progressTracker->getProgress();
    }
    
    Result<std::vector<FileMetadata>> listFiles(
        size_t offset,
        size_t limit
    ) const override {
        return m_database->getFiles(m_sessionId, offset, limit);
    }
    
    Result<std::vector<FileMetadata>> listFilesByCategory(
        FileCategory category,
        size_t offset,
        size_t limit
    ) const override {
        return m_database->getFilesByCategory(m_sessionId, category, offset, limit);
    }
    
    Result<std::vector<FileMetadata>> listFilesByExtension(
        const std::string& extension,
        size_t offset,
        size_t limit
    ) const override {
        return m_database->getFilesByExtension(m_sessionId, extension, offset, limit);
    }
    
    Result<std::vector<FileMetadata>> searchFiles(
        const std::string& pattern,
        size_t offset,
        size_t limit
    ) const override {
        // Convert wildcards to SQL LIKE pattern
        std::string sqlPattern = pattern;
        for (char& c : sqlPattern) {
            if (c == '*') c = '%';
            else if (c == '?') c = '_';
        }
        return m_database->searchFiles(m_sessionId, sqlPattern, offset, limit);
    }
    
    Result<std::vector<std::pair<FileCategory, size_t>>> getFileCounts() const override {
        return m_database->getFileCounts(m_sessionId);
    }
    
    size_t getTotalFileCount() const override {
        auto result = m_database->getTotalFileCount(m_sessionId);
        return result.isSuccess() ? result.value : 0;
    }
    
    size_t getDeletedFileCount() const override {
        auto result = m_database->getDeletedFileCount(m_sessionId);
        return result.isSuccess() ? result.value : 0;
    }
    
    Result<RecoveryProgress> recoverFiles(
        const std::vector<uint64_t>& fileIds,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback
    ) override {
        if (fileIds.empty()) {
            return Result<RecoveryProgress>::failure(
                RecoveryError::InvalidParameter,
                "No files specified for recovery"
            );
        }
        
        // Validate destination
        if (options.destinationPath.empty()) {
            return Result<RecoveryProgress>::failure(
                RecoveryError::InvalidPath,
                "Destination path not specified"
            );
        }
        
        fs::path destPath(options.destinationPath);
        if (!fs::exists(destPath)) {
            try {
                fs::create_directories(destPath);
            } catch (const std::exception& e) {
                return Result<RecoveryProgress>::failure(
                    RecoveryError::WriteError,
                    std::string("Cannot create destination: ") + e.what()
                );
            }
        }
        
        // Get file metadata
        std::vector<FileMetadata> files;
        uint64_t totalBytes = 0;
        
        for (uint64_t id : fileIds) {
            auto result = m_database->getFile(m_sessionId, id);
            if (result.isSuccess()) {
                files.push_back(result.value);
                totalBytes += result.value.fileSize;
            }
        }
        
        // Set up recovery tracking
        m_recoveryTracker->reset(files.size(), totalBytes);
        m_recoveryTracker->setCallback(callback);
        m_recoveryTracker->setStatus(OperationStatus::Running);
        
        // Open source for reading
        FileSystemParser parser(m_devicePath);
        auto openResult = parser.open();
        if (openResult.isError()) {
            return Result<RecoveryProgress>::failure(openResult.error, openResult.errorMessage);
        }
        
        // Recover each file
        for (const auto& file : files) {
            if (m_recoveryTracker->isCancelled()) {
                break;
            }
            
            m_recoveryTracker->waitWhilePaused();
            m_recoveryTracker->setCurrentFile(file.fileName);
            
            auto recoverResult = recoverSingleFile(parser, file, options);
            
            if (recoverResult.isSuccess()) {
                m_recoveryTracker->markFileRecovered(file.fileSize);
                m_database->updateFileRecoveryStatus(
                    m_sessionId, file.fileId, true, recoverResult.value
                );
            } else {
                m_recoveryTracker->markFileFailed(
                    recoverResult.error, recoverResult.errorMessage
                );
            }
            
            m_recoveryTracker->notifyIfNeeded();
        }
        
        m_recoveryTracker->setStatus(OperationStatus::Completed);
        return Result<RecoveryProgress>::success(m_recoveryTracker->getProgress());
    }
    
    Result<RecoveryProgress> recoverAll(
        const RecoveryOptions& options,
        RecoveryProgressCallback callback
    ) override {
        auto filesResult = listFiles();
        if (filesResult.isError()) {
            return Result<RecoveryProgress>::failure(filesResult.error, filesResult.errorMessage);
        }
        
        std::vector<uint64_t> fileIds;
        for (const auto& file : filesResult.value) {
            // Apply confidence filter
            if (file.recoveryConfidence >= options.minRecoveryConfidence) {
                fileIds.push_back(file.fileId);
            }
        }
        
        return recoverFiles(fileIds, options, callback);
    }
    
    Result<void> pauseRecovery() override {
        m_recoveryTracker->requestPause();
        m_recoveryTracker->setStatus(OperationStatus::Paused);
        return Result<void>::success();
    }
    
    Result<void> resumeRecovery() override {
        m_recoveryTracker->requestResume();
        m_recoveryTracker->setStatus(OperationStatus::Running);
        return Result<void>::success();
    }
    
    Result<void> cancelRecovery() override {
        m_recoveryTracker->requestCancel();
        m_recoveryTracker->setStatus(OperationStatus::Cancelled);
        return Result<void>::success();
    }
    
    RecoveryProgress getRecoveryProgress() const override {
        return m_recoveryTracker->getProgress();
    }
    
    Result<void> save() override {
        SessionState state = getState();
        return m_database->updateSession(m_sessionId, state);
    }
    
    Result<void> exportResults(
        const std::string& path,
        const std::string& format
    ) const override {
        auto filesResult = listFiles();
        if (filesResult.isError()) {
            return Result<void>::failure(filesResult.error, filesResult.errorMessage);
        }
        
        std::ofstream file(path);
        if (!file.is_open()) {
            return Result<void>::failure(
                RecoveryError::WriteError,
                "Cannot open export file"
            );
        }
        
        if (format == "json") {
            exportAsJson(file, filesResult.value);
        } else if (format == "csv") {
            exportAsCsv(file, filesResult.value);
        } else {
            return Result<void>::failure(
                RecoveryError::InvalidParameter,
                "Unsupported export format"
            );
        }
        
        return Result<void>::success();
    }
    
private:
    std::string m_sessionId;
    std::string m_sessionName;
    std::string m_devicePath;
    ScanOptions m_scanOptions;
    
    std::shared_ptr<SessionDatabase> m_database;
    std::unique_ptr<ProgressTracker> m_progressTracker;
    std::unique_ptr<RecoveryProgressTracker> m_recoveryTracker;
    
    std::mutex m_scanMutex;
    
    Result<void> performQuickScan(
        const std::string& volumePath,
        const ScanOptions& options,
        FileFoundCallback fileCallback
    ) {
        FileSystemParser parser(volumePath);
        auto openResult = parser.open();
        if (openResult.isError()) {
            m_progressTracker->setStatus(OperationStatus::Failed);
            return openResult;
        }
        
        auto volInfo = parser.getVolumeInfo();
        if (volInfo.isSuccess()) {
            m_progressTracker->reset(
                volInfo.value.totalSize,
                0, // Unknown item count
                ScanType::QuickScan
            );
        }
        
        // File callback wrapper to store in database
        auto wrappedCallback = [this, &fileCallback](const FileMetadata& file) {
            // Store in database
            m_database->addFile(m_sessionId, file);
            
            // Update progress
            m_progressTracker->incrementFilesFound(file.isDeleted, false);
            m_progressTracker->setCurrentPath(file.filePath);
            m_progressTracker->notifyIfNeeded();
            
            // Forward to user callback
            if (fileCallback) {
                fileCallback(file);
            }
        };
        
        auto progressCallback = [this](const ScanProgress& progress) {
            // Progress from parser is forwarded
        };
        
        auto enumResult = parser.enumerateFiles(options, wrappedCallback, progressCallback);
        
        if (enumResult.isError()) {
            m_progressTracker->setStatus(OperationStatus::Failed);
            return Result<void>::failure(enumResult.error, enumResult.errorMessage);
        }
        
        m_progressTracker->setStatus(OperationStatus::Completed);
        save();
        
        return Result<void>::success();
    }
    
    Result<void> performDeepScan(
        const std::string& volumePath,
        const ScanOptions& options,
        FileFoundCallback fileCallback
    ) {
        // First, do a quick scan for filesystem data
        auto quickResult = performQuickScan(volumePath, options, fileCallback);
        
        // Then perform deep scan/carving
        DeepScanner deepScanner(volumePath);
        
        auto wrappedCallback = [this, &fileCallback](const FileMetadata& file) {
            m_database->addFile(m_sessionId, file);
            m_progressTracker->incrementFilesFound(file.isDeleted, true);
            m_progressTracker->notifyIfNeeded();
            
            if (fileCallback) {
                fileCallback(file);
            }
        };
        
        auto progressCallback = [this](const ScanProgress& progress) {
            m_progressTracker->updateBytes(progress.scannedBytes);
            m_progressTracker->notifyIfNeeded();
        };
        
        m_progressTracker->setCurrentOperation("Deep scanning for deleted files...");
        
        auto deepResult = deepScanner.scanUnallocatedSpace(
            options, wrappedCallback, progressCallback
        );
        
        if (deepResult.isError()) {
            // Deep scan failure is non-fatal, we still have quick scan results
        }
        
        m_progressTracker->setStatus(OperationStatus::Completed);
        save();
        
        return Result<void>::success();
    }
    
    Result<std::string> recoverSingleFile(
        FileSystemParser& parser,
        const FileMetadata& file,
        const RecoveryOptions& options
    ) {
        // Build destination path
        fs::path destDir(options.destinationPath);
        fs::path destFile;
        
        if (options.preserveFolderStructure && !file.filePath.empty()) {
            // Preserve original folder structure
            fs::path relativePath(file.filePath);
            destFile = destDir / relativePath;
            fs::create_directories(destFile.parent_path());
        } else {
            destFile = destDir / file.fileName;
        }
        
        // Handle conflicts
        if (fs::exists(destFile)) {
            switch (options.onConflict) {
                case RecoveryOptions::ConflictAction::Skip:
                    m_recoveryTracker->markFileSkipped();
                    return Result<std::string>::success(destFile.string());
                    
                case RecoveryOptions::ConflictAction::Overwrite:
                    // Continue to overwrite
                    break;
                    
                case RecoveryOptions::ConflictAction::Rename: {
                    // Generate unique name
                    int counter = 1;
                    fs::path stem = destFile.stem();
                    fs::path ext = destFile.extension();
                    fs::path parent = destFile.parent_path();
                    
                    while (fs::exists(destFile)) {
                        std::string newName = stem.string() + "_" + 
                                            std::to_string(counter++) + ext.string();
                        destFile = parent / newName;
                    }
                    break;
                }
                
                default:
                    return Result<std::string>::failure(
                        RecoveryError::WriteError,
                        "File exists and no conflict resolution specified"
                    );
            }
        }
        
        // Read file content
        auto dataResult = parser.readFile(file.fileId);
        if (dataResult.isError()) {
            return Result<std::string>::failure(dataResult.error, dataResult.errorMessage);
        }
        
        // Write to destination
        std::ofstream outFile(destFile, std::ios::binary);
        if (!outFile.is_open()) {
            return Result<std::string>::failure(
                RecoveryError::WriteError,
                "Cannot create output file"
            );
        }
        
        outFile.write(
            reinterpret_cast<const char*>(dataResult.value.data()),
            dataResult.value.size()
        );
        
        if (!outFile.good()) {
            return Result<std::string>::failure(
                RecoveryError::WriteError,
                "Error writing file data"
            );
        }
        
        outFile.close();
        
        // Restore timestamps if requested
        if (options.preserveTimestamps && file.modifiedTime > 0) {
            try {
                auto modTime = std::chrono::system_clock::from_time_t(file.modifiedTime);
                fs::last_write_time(destFile, 
                    std::chrono::clock_cast<std::chrono::file_clock>(modTime));
            } catch (...) {
                // Timestamp restoration failure is non-fatal
            }
        }
        
        return Result<std::string>::success(destFile.string());
    }
    
    void exportAsJson(std::ostream& out, const std::vector<FileMetadata>& files) const {
        out << "{\n  \"session\": {\n";
        out << "    \"id\": \"" << m_sessionId << "\",\n";
        out << "    \"name\": \"" << m_sessionName << "\",\n";
        out << "    \"device\": \"" << m_devicePath << "\"\n";
        out << "  },\n";
        out << "  \"files\": [\n";
        
        for (size_t i = 0; i < files.size(); ++i) {
            const auto& f = files[i];
            out << "    {\n";
            out << "      \"id\": " << f.fileId << ",\n";
            out << "      \"name\": \"" << f.fileName << "\",\n";
            out << "      \"path\": \"" << f.filePath << "\",\n";
            out << "      \"size\": " << f.fileSize << ",\n";
            out << "      \"category\": \"" << fileCategoryToString(f.category) << "\",\n";
            out << "      \"score\": \"" << recoveryScoreToString(f.score) << "\",\n";
            out << "      \"confidence\": " << f.recoveryConfidence << ",\n";
            out << "      \"deleted\": " << (f.isDeleted ? "true" : "false") << "\n";
            out << "    }" << (i < files.size() - 1 ? "," : "") << "\n";
        }
        
        out << "  ]\n}\n";
    }
    
    void exportAsCsv(std::ostream& out, const std::vector<FileMetadata>& files) const {
        out << "ID,Name,Path,Size,Category,Score,Confidence,Deleted\n";
        
        for (const auto& f : files) {
            out << f.fileId << ","
                << "\"" << f.fileName << "\","
                << "\"" << f.filePath << "\","
                << f.fileSize << ","
                << fileCategoryToString(f.category) << ","
                << recoveryScoreToString(f.score) << ","
                << f.recoveryConfidence << ","
                << (f.isDeleted ? "true" : "false") << "\n";
        }
    }
};

// ============================================================================
// DataRecoveryEngineImpl - Implementation of main engine interface
// ============================================================================

class DataRecoveryEngineImpl : public DataRecoveryEngine {
public:
    explicit DataRecoveryEngineImpl(const std::string& databasePath)
        : m_databasePath(databasePath.empty() ? getDefaultDatabasePath() : databasePath)
    {
        m_database = std::make_shared<SessionDatabase>(m_databasePath);
        m_deviceScanner = std::make_unique<DeviceScanner>();
        m_volumeScanner = std::make_unique<VolumeScanner>();
        m_fileCarver = std::make_unique<FileCarver>();
        
        // Load default carving signatures
        m_fileCarver->loadDefaultSignatures();
        
        // Open database
        m_database->open();
    }
    
    ~DataRecoveryEngineImpl() override {
        m_database->close();
    }
    
    std::string getVersion() const override {
        return ENGINE_VERSION;
    }
    
    std::string getTSKVersion() const override {
        return tsk_version_get_str();
    }
    
    Result<std::vector<DeviceInfo>> scanDevices() override {
        return m_deviceScanner->scanDevices();
    }
    
    Result<DeviceInfo> getDeviceInfo(const std::string& devicePath) override {
        return m_deviceScanner->getDeviceInfo(devicePath);
    }
    
    Result<std::vector<VolumeInfo>> scanVolumes(const std::string& devicePath) override {
        return m_volumeScanner->scanVolumes(devicePath);
    }
    
    Result<std::vector<VolumeInfo>> detectLostPartitions(
        const std::string& devicePath,
        ProgressCallback callback
    ) override {
        return m_volumeScanner->detectLostPartitions(devicePath, callback);
    }
    
    std::shared_ptr<RecoverySession> createSession(
        const std::string& sessionName
    ) override {
        std::string sessionId = generateSessionId();
        std::string name = sessionName.empty() ? 
            ("Session_" + sessionId.substr(0, 8)) : sessionName;
        
        // Create session in database
        m_database->createSession(name, "");
        
        auto session = std::make_shared<RecoverySessionImpl>(
            sessionId, name, m_database
        );
        
        std::lock_guard<std::mutex> lock(m_sessionsMutex);
        m_activeSessions[sessionId] = session;
        
        return session;
    }
    
    Result<std::shared_ptr<RecoverySession>> loadSession(
        const std::string& sessionId
    ) override {
        // Check if already loaded
        {
            std::lock_guard<std::mutex> lock(m_sessionsMutex);
            auto it = m_activeSessions.find(sessionId);
            if (it != m_activeSessions.end()) {
                return Result<std::shared_ptr<RecoverySession>>::success(it->second);
            }
        }
        
        // Load from database
        auto stateResult = m_database->getSession(sessionId);
        if (stateResult.isError()) {
            return Result<std::shared_ptr<RecoverySession>>::failure(
                stateResult.error, stateResult.errorMessage
            );
        }
        
        auto session = std::make_shared<RecoverySessionImpl>(
            stateResult.value.sessionId,
            stateResult.value.sessionName,
            m_database
        );
        
        std::lock_guard<std::mutex> lock(m_sessionsMutex);
        m_activeSessions[sessionId] = session;
        
        return Result<std::shared_ptr<RecoverySession>>::success(session);
    }
    
    Result<std::vector<SessionState>> listSessions() override {
        return m_database->listSessions();
    }
    
    Result<void> deleteSession(const std::string& sessionId) override {
        {
            std::lock_guard<std::mutex> lock(m_sessionsMutex);
            m_activeSessions.erase(sessionId);
        }
        return m_database->deleteSession(sessionId);
    }
    
    Result<std::vector<FileMetadata>> quickScan(
        const std::string& volumePath,
        const ScanOptions& options,
        ProgressCallback progressCallback,
        FileFoundCallback fileCallback
    ) override {
        auto session = createSession();
        
        ScanOptions opts = options;
        opts.scanType = ScanType::QuickScan;
        
        auto scanResult = session->scanVolume(volumePath, opts, progressCallback, fileCallback);
        if (scanResult.isError()) {
            return Result<std::vector<FileMetadata>>::failure(
                scanResult.error, scanResult.errorMessage
            );
        }
        
        return session->listFiles();
    }
    
    Result<std::vector<FileMetadata>> deepScan(
        const std::string& volumePath,
        const ScanOptions& options,
        ProgressCallback progressCallback,
        FileFoundCallback fileCallback
    ) override {
        auto session = createSession();
        
        ScanOptions opts = options;
        opts.scanType = ScanType::DeepScan;
        
        auto scanResult = session->scanVolume(volumePath, opts, progressCallback, fileCallback);
        if (scanResult.isError()) {
            return Result<std::vector<FileMetadata>>::failure(
                scanResult.error, scanResult.errorMessage
            );
        }
        
        return session->listFiles();
    }
    
    Result<std::vector<uint8_t>> previewFile(
        const std::string& volumePath,
        uint64_t fileId,
        size_t maxBytes
    ) override {
        FileSystemParser parser(volumePath);
        auto openResult = parser.open();
        if (openResult.isError()) {
            return Result<std::vector<uint8_t>>::failure(
                openResult.error, openResult.errorMessage
            );
        }
        
        return parser.readFile(fileId, 0, maxBytes);
    }
    
    Result<void> recoverFile(
        const std::string& volumePath,
        uint64_t fileId,
        const std::string& destinationPath,
        RecoveryProgressCallback callback
    ) override {
        auto session = createSession();
        
        // We need to scan to populate the session first
        ScanOptions scanOpts;
        scanOpts.scanType = ScanType::QuickScan;
        session->scanVolume(volumePath, scanOpts);
        
        RecoveryOptions recOpts;
        recOpts.destinationPath = fs::path(destinationPath).parent_path().string();
        recOpts.preserveFolderStructure = false;
        
        std::vector<uint64_t> fileIds = {fileId};
        auto result = session->recoverFiles(fileIds, recOpts, callback);
        
        if (result.isError()) {
            return Result<void>::failure(result.error, result.errorMessage);
        }
        
        return Result<void>::success();
    }
    
    Result<RecoveryProgress> recoverFiles(
        const std::string& volumePath,
        const std::vector<uint64_t>& fileIds,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback
    ) override {
        auto session = createSession();
        
        ScanOptions scanOpts;
        scanOpts.scanType = ScanType::QuickScan;
        session->scanVolume(volumePath, scanOpts);
        
        return session->recoverFiles(fileIds, options, callback);
    }
    
    Result<RecoveryProgress> recoverAll(
        const std::string& volumePath,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback
    ) override {
        auto session = createSession();
        
        ScanOptions scanOpts;
        scanOpts.scanType = options.recoverPartialFiles ? 
            ScanType::DeepScan : ScanType::QuickScan;
        session->scanVolume(volumePath, scanOpts);
        
        return session->recoverAll(options, callback);
    }
    
    Result<FileMetadata> getMetadata(
        const std::string& volumePath,
        uint64_t fileId
    ) override {
        FileSystemParser parser(volumePath);
        auto openResult = parser.open();
        if (openResult.isError()) {
            return Result<FileMetadata>::failure(openResult.error, openResult.errorMessage);
        }
        
        return parser.getFileByInode(fileId);
    }
    
    Result<std::pair<RecoveryScore, float>> getRecoveryScore(
        const std::string& volumePath,
        uint64_t fileId
    ) override {
        FileSystemParser parser(volumePath);
        auto openResult = parser.open();
        if (openResult.isError()) {
            return Result<std::pair<RecoveryScore, float>>::failure(
                openResult.error, openResult.errorMessage
            );
        }
        
        return parser.analyzeRecoveryPotential(fileId);
    }
    
    bool isValidSource(const std::string& path) override {
        if (path.empty()) return false;
        
        // Check for device path format
        if (path.find("\\\\.\\") == 0) {
            return m_deviceScanner->isValidDevice(path);
        }
        
        // Check for file/directory
        return fs::exists(path);
    }
    
    Result<FilesystemType> detectFilesystem(const std::string& volumePath) override {
        return m_volumeScanner->detectFilesystem(volumePath);
    }
    
    void cancelAll() override {
        std::lock_guard<std::mutex> lock(m_sessionsMutex);
        for (auto& [id, session] : m_activeSessions) {
            session->cancelScan();
            session->cancelRecovery();
        }
    }
    
    void setErrorCallback(ErrorCallback callback) override {
        m_errorCallback = callback;
    }
    
private:
    std::string m_databasePath;
    std::shared_ptr<SessionDatabase> m_database;
    std::unique_ptr<DeviceScanner> m_deviceScanner;
    std::unique_ptr<VolumeScanner> m_volumeScanner;
    std::unique_ptr<FileCarver> m_fileCarver;
    
    std::mutex m_sessionsMutex;
    std::map<std::string, std::shared_ptr<RecoverySession>> m_activeSessions;
    
    ErrorCallback m_errorCallback;
};

// Factory function implementation
std::shared_ptr<DataRecoveryEngine> DataRecoveryEngine::create(
    const std::string& databasePath
) {
    return std::make_shared<DataRecoveryEngineImpl>(databasePath);
}

} // namespace RecoveryEngine

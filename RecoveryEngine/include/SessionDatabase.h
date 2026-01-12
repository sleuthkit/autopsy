/**
 * @file SessionDatabase.h
 * @brief SQLite-based session and results persistence
 */

#ifndef SESSION_DATABASE_H
#define SESSION_DATABASE_H

#include "RecoveryTypes.h"
#include <string>
#include <vector>
#include <memory>

struct sqlite3;

namespace RecoveryEngine {

/**
 * @class SessionDatabase
 * @brief Manages SQLite database for session persistence
 * 
 * Stores scan sessions, discovered files, and scan progress to enable
 * pause/resume functionality and session management.
 */
class SessionDatabase {
public:
    /**
     * @brief Construct database with specified path
     * @param dbPath Path to SQLite database file
     */
    explicit SessionDatabase(const std::string& dbPath);
    ~SessionDatabase();
    
    // Non-copyable
    SessionDatabase(const SessionDatabase&) = delete;
    SessionDatabase& operator=(const SessionDatabase&) = delete;
    
    /**
     * @brief Open or create the database
     * @return Success or error
     */
    Result<void> open();
    
    /**
     * @brief Close the database
     */
    void close();
    
    /**
     * @brief Check if database is open
     */
    bool isOpen() const;
    
    /**
     * @brief Get database file path
     */
    std::string getDatabasePath() const;
    
    // =========================================================================
    // Session Management
    // =========================================================================
    
    /**
     * @brief Create a new session
     * @param name Session name
     * @param devicePath Device being scanned
     * @return New session ID
     */
    Result<std::string> createSession(
        const std::string& name,
        const std::string& devicePath
    );
    
    /**
     * @brief Get session state
     * @param sessionId Session ID
     * @return Session state
     */
    Result<SessionState> getSession(const std::string& sessionId);
    
    /**
     * @brief Update session state
     * @param sessionId Session ID
     * @param state New state
     * @return Success or error
     */
    Result<void> updateSession(
        const std::string& sessionId,
        const SessionState& state
    );
    
    /**
     * @brief Delete a session and all its data
     * @param sessionId Session ID
     * @return Success or error
     */
    Result<void> deleteSession(const std::string& sessionId);
    
    /**
     * @brief List all sessions
     * @return List of session states
     */
    Result<std::vector<SessionState>> listSessions();
    
    /**
     * @brief Update scan progress for session
     * @param sessionId Session ID
     * @param progress Current progress
     * @return Success or error
     */
    Result<void> updateScanProgress(
        const std::string& sessionId,
        const ScanProgress& progress
    );
    
    /**
     * @brief Get scan progress for session
     * @param sessionId Session ID
     * @return Scan progress
     */
    Result<ScanProgress> getScanProgress(const std::string& sessionId);
    
    // =========================================================================
    // File Results
    // =========================================================================
    
    /**
     * @brief Add a found file to session
     * @param sessionId Session ID
     * @param file File metadata
     * @return Internal file ID
     */
    Result<uint64_t> addFile(
        const std::string& sessionId,
        const FileMetadata& file
    );
    
    /**
     * @brief Add multiple files (batch insert)
     * @param sessionId Session ID
     * @param files List of file metadata
     * @return Success or error
     */
    Result<void> addFiles(
        const std::string& sessionId,
        const std::vector<FileMetadata>& files
    );
    
    /**
     * @brief Get file by ID
     * @param sessionId Session ID
     * @param fileId File ID
     * @return File metadata
     */
    Result<FileMetadata> getFile(
        const std::string& sessionId,
        uint64_t fileId
    );
    
    /**
     * @brief Get all files in session
     * @param sessionId Session ID
     * @param offset Pagination offset
     * @param limit Maximum results (0 = all)
     * @return List of files
     */
    Result<std::vector<FileMetadata>> getFiles(
        const std::string& sessionId,
        size_t offset = 0,
        size_t limit = 0
    );
    
    /**
     * @brief Get files by category
     * @param sessionId Session ID
     * @param category File category
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Filtered files
     */
    Result<std::vector<FileMetadata>> getFilesByCategory(
        const std::string& sessionId,
        FileCategory category,
        size_t offset = 0,
        size_t limit = 0
    );
    
    /**
     * @brief Get files by extension
     * @param sessionId Session ID
     * @param extension File extension
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Filtered files
     */
    Result<std::vector<FileMetadata>> getFilesByExtension(
        const std::string& sessionId,
        const std::string& extension,
        size_t offset = 0,
        size_t limit = 0
    );
    
    /**
     * @brief Search files by name pattern
     * @param sessionId Session ID
     * @param pattern Search pattern (SQL LIKE syntax: % and _)
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Matching files
     */
    Result<std::vector<FileMetadata>> searchFiles(
        const std::string& sessionId,
        const std::string& pattern,
        size_t offset = 0,
        size_t limit = 0
    );
    
    /**
     * @brief Get file count by category
     * @param sessionId Session ID
     * @return Map of category to count
     */
    Result<std::vector<std::pair<FileCategory, size_t>>> getFileCounts(
        const std::string& sessionId
    );
    
    /**
     * @brief Get total file count
     * @param sessionId Session ID
     * @return Total files
     */
    Result<size_t> getTotalFileCount(const std::string& sessionId);
    
    /**
     * @brief Get deleted file count
     * @param sessionId Session ID
     * @return Deleted files count
     */
    Result<size_t> getDeletedFileCount(const std::string& sessionId);
    
    /**
     * @brief Update file recovery status
     * @param sessionId Session ID
     * @param fileId File ID
     * @param recovered True if successfully recovered
     * @param recoveredPath Path where file was recovered
     * @return Success or error
     */
    Result<void> updateFileRecoveryStatus(
        const std::string& sessionId,
        uint64_t fileId,
        bool recovered,
        const std::string& recoveredPath = ""
    );
    
    // =========================================================================
    // Database Maintenance
    // =========================================================================
    
    /**
     * @brief Vacuum database to reclaim space
     */
    Result<void> vacuum();
    
    /**
     * @brief Begin a transaction
     */
    Result<void> beginTransaction();
    
    /**
     * @brief Commit transaction
     */
    Result<void> commitTransaction();
    
    /**
     * @brief Rollback transaction
     */
    Result<void> rollbackTransaction();
    
    /**
     * @brief Get database size in bytes
     */
    uint64_t getDatabaseSize() const;
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
    
    // Schema management
    Result<void> createSchema();
    Result<void> upgradeSchema(int fromVersion, int toVersion);
    int getSchemaVersion();
};

} // namespace RecoveryEngine

#endif // SESSION_DATABASE_H

/**
 * @file FileCarver.h
 * @brief File signature detection and carving
 */

#ifndef FILE_CARVER_H
#define FILE_CARVER_H

#include "RecoveryTypes.h"
#include <vector>
#include <string>
#include <memory>
#include <map>

namespace RecoveryEngine {

/**
 * @struct FileSignature
 * @brief Defines a file type signature for carving
 */
struct FileSignature {
    std::string name;                       ///< Human-readable name
    std::string extension;                  ///< File extension
    FileCategory category;                  ///< File category
    std::vector<uint8_t> headerSignature;   ///< Header magic bytes
    size_t headerOffset = 0;                ///< Offset of header signature
    std::vector<uint8_t> footerSignature;   ///< Footer signature (optional)
    size_t maxFileSize = 100 * 1024 * 1024; ///< Maximum file size to carve
    size_t minFileSize = 1;                 ///< Minimum valid file size
    std::string mimeType;                   ///< MIME type
    
    // For signatures that need validation
    using ValidatorFunc = std::function<bool(const uint8_t*, size_t)>;
    ValidatorFunc validator;                ///< Optional validation function
};

/**
 * @struct CarvedFile
 * @brief Information about a carved file
 */
struct CarvedFile {
    uint64_t startOffset;       ///< Start offset on disk
    uint64_t endOffset;         ///< End offset on disk
    uint64_t size;              ///< Calculated file size
    std::string extension;      ///< File extension
    FileCategory category;      ///< File category
    std::string mimeType;       ///< MIME type
    float confidence;           ///< Carving confidence (0-1)
    bool hasValidFooter;        ///< Footer was found
    bool passedValidation;      ///< Passed structure validation
};

/**
 * @class FileCarver
 * @brief Carves files from raw disk data using signatures
 * 
 * Identifies file types by their magic numbers (header signatures)
 * and optionally validates file structure. Supports common image,
 * video, document, and archive formats.
 */
class FileCarver {
public:
    FileCarver();
    ~FileCarver();
    
    // Non-copyable
    FileCarver(const FileCarver&) = delete;
    FileCarver& operator=(const FileCarver&) = delete;
    
    /**
     * @brief Load default file signatures
     * 
     * Loads signatures for common file types: JPEG, PNG, GIF, BMP,
     * PDF, DOCX, XLSX, ZIP, RAR, MP3, MP4, AVI, etc.
     */
    void loadDefaultSignatures();
    
    /**
     * @brief Add a custom file signature
     * @param signature Signature definition
     */
    void addSignature(const FileSignature& signature);
    
    /**
     * @brief Remove a signature by extension
     * @param extension File extension to remove
     */
    void removeSignature(const std::string& extension);
    
    /**
     * @brief Get all loaded signatures
     */
    std::vector<FileSignature> getSignatures() const;
    
    /**
     * @brief Scan buffer for file signatures
     * @param buffer Data buffer
     * @param bufferSize Buffer size
     * @param baseOffset Offset on disk where buffer starts
     * @return List of signature matches found
     */
    std::vector<std::pair<size_t, const FileSignature*>> findSignatures(
        const uint8_t* buffer,
        size_t bufferSize,
        uint64_t baseOffset = 0
    );
    
    /**
     * @brief Carve a single file starting at given offset
     * @param reader Function to read data: (offset, buffer, size) -> bytes_read
     * @param startOffset Where signature was found
     * @param signature The matching signature
     * @param maxSearchSize Maximum bytes to search for footer
     * @return Carved file information
     */
    using DataReader = std::function<size_t(uint64_t, uint8_t*, size_t)>;
    Result<CarvedFile> carveFile(
        DataReader reader,
        uint64_t startOffset,
        const FileSignature& signature,
        size_t maxSearchSize = 0
    );
    
    /**
     * @brief Validate carved file structure
     * @param reader Data reader function
     * @param carved Carved file info to validate
     * @return True if file appears valid
     */
    bool validateCarvedFile(DataReader reader, const CarvedFile& carved);
    
    /**
     * @brief Extract carved file to buffer
     * @param reader Data reader function
     * @param carved Carved file info
     * @return File data
     */
    Result<std::vector<uint8_t>> extractCarvedFile(
        DataReader reader,
        const CarvedFile& carved
    );
    
    /**
     * @brief Set enabled categories for carving
     * @param categories Categories to carve (empty = all)
     */
    void setEnabledCategories(const std::vector<FileCategory>& categories);
    
    /**
     * @brief Set enabled extensions for carving
     * @param extensions Extensions to carve (empty = all)
     */
    void setEnabledExtensions(const std::vector<std::string>& extensions);
    
    /**
     * @brief Set maximum file size for carving
     * @param maxSize Maximum size in bytes (per file)
     */
    void setMaxFileSize(size_t maxSize);
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
    
    // Specialized validators
    bool validateJPEG(const uint8_t* data, size_t size);
    bool validatePNG(const uint8_t* data, size_t size);
    bool validatePDF(const uint8_t* data, size_t size);
    bool validateZIP(const uint8_t* data, size_t size);
    bool validateMP3(const uint8_t* data, size_t size);
    bool validateMP4(const uint8_t* data, size_t size);
};

/**
 * @brief Get default file signatures for common types
 */
std::vector<FileSignature> getDefaultFileSignatures();

} // namespace RecoveryEngine

#endif // FILE_CARVER_H

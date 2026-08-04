#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>

namespace {

struct MapsSummary {
    std::size_t records = 0;
    std::size_t executable = 0;
    std::size_t anonymous_executable = 0;
    std::size_t deleted_backing = 0;
    std::size_t suspicious_name_hits = 0;
    std::size_t malformed = 0;
    bool truncated = false;
};

struct MountSummary {
    std::size_t records = 0;
    std::size_t overlay = 0;
    std::size_t proc = 0;
    std::size_t tmpfs = 0;
    std::size_t malformed = 0;
    bool truncated = false;
};

struct StatusSummary {
    bool tracer_present = false;
    std::uint64_t tracer_pid = 0;
    bool no_new_privs_present = false;
    std::uint64_t no_new_privs = 0;
    bool seccomp_present = false;
    std::uint64_t seccomp = 0;
    std::size_t malformed = 0;
};

constexpr std::size_t kMaxMapsLines = 16384;
constexpr std::size_t kMaxMountLines = 8192;

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char character) {
        return static_cast<char>(std::tolower(character));
    });
    return value;
}

bool parse_u64(std::string value, std::uint64_t& output) {
    const auto first = value.find_first_not_of(" \t\r");
    if (first == std::string::npos) return false;
    const auto last = value.find_last_not_of(" \t\r");
    value = value.substr(first, last - first + 1);
    try {
        std::size_t consumed = 0;
        const auto parsed = std::stoull(value, &consumed, 10);
        if (consumed != value.size()) return false;
        output = parsed;
        return true;
    } catch (...) {
        return false;
    }
}

MapsSummary read_maps() {
    MapsSummary summary;
    std::ifstream input("/proc/self/maps");
    if (!input) {
        summary.malformed = 1;
        return summary;
    }
    std::string line;
    while (std::getline(input, line)) {
        if (summary.records + summary.malformed >= kMaxMapsLines) {
            summary.truncated = true;
            break;
        }
        std::istringstream fields(line);
        std::string range, permissions, offset, device, inode;
        if (!(fields >> range >> permissions >> offset >> device >> inode)) {
            ++summary.malformed;
            continue;
        }
        ++summary.records;
        const bool executable = permissions.size() >= 3 && permissions[2] == 'x';
        if (executable) ++summary.executable;
        std::string path;
        std::getline(fields, path);
        const auto first = path.find_first_not_of(" \t");
        if (first == std::string::npos) path.clear(); else path.erase(0, first);
        const bool anonymous = path.empty() || path.front() == '[';
        if (executable && anonymous) ++summary.anonymous_executable;
        if (path.find(" (deleted)") != std::string::npos) ++summary.deleted_backing;
        const auto path_lower = lower(path);
        for (const char* marker : {"frida", "xposed", "lsposed", "substrate", "zygisk"}) {
            if (path_lower.find(marker) != std::string::npos) {
                ++summary.suspicious_name_hits;
                break;
            }
        }
    }
    return summary;
}

MountSummary read_mounts() {
    MountSummary summary;
    std::ifstream input("/proc/self/mountinfo");
    if (!input) {
        summary.malformed = 1;
        return summary;
    }
    std::string line;
    while (std::getline(input, line)) {
        if (summary.records + summary.malformed >= kMaxMountLines) {
            summary.truncated = true;
            break;
        }
        const auto separator = line.find(" - ");
        if (separator == std::string::npos) {
            ++summary.malformed;
            continue;
        }
        std::istringstream fields(line.substr(separator + 3));
        std::string filesystem;
        if (!(fields >> filesystem)) {
            ++summary.malformed;
            continue;
        }
        ++summary.records;
        if (filesystem == "overlay") ++summary.overlay;
        else if (filesystem == "proc") ++summary.proc;
        else if (filesystem == "tmpfs") ++summary.tmpfs;
    }
    return summary;
}

StatusSummary read_status() {
    StatusSummary summary;
    std::ifstream input("/proc/self/status");
    if (!input) {
        summary.malformed = 1;
        return summary;
    }
    std::string line;
    while (std::getline(input, line)) {
        const auto separator = line.find(':');
        if (separator == std::string::npos) continue;
        const auto key = line.substr(0, separator);
        const auto value = line.substr(separator + 1);
        std::uint64_t parsed = 0;
        if (key == "TracerPid") {
            summary.tracer_present = true;
            if (parse_u64(value, parsed)) summary.tracer_pid = parsed; else ++summary.malformed;
        } else if (key == "NoNewPrivs") {
            summary.no_new_privs_present = true;
            if (parse_u64(value, parsed)) summary.no_new_privs = parsed; else ++summary.malformed;
        } else if (key == "Seccomp") {
            summary.seccomp_present = true;
            if (parse_u64(value, parsed)) summary.seccomp = parsed; else ++summary.malformed;
        }
    }
    return summary;
}

std::uint64_t fnv1a(const std::uint8_t* data, std::size_t size) {
    std::uint64_t hash = 14695981039346656037ULL;
    for (std::size_t index = 0; index < size; ++index) {
        hash ^= data[index];
        hash *= 1099511628211ULL;
    }
    return hash;
}

std::string bool_json(bool value) {
    return value ? "true" : "false";
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_nullweave_parc_android_controlplane_NativeRuntimeProbe_collectJson(
    JNIEnv* env,
    jobject,
    jbyteArray challenge) {
    const auto maps = read_maps();
    const auto mounts = read_mounts();
    const auto status = read_status();

    const auto challenge_size = challenge == nullptr ? 0 : static_cast<std::size_t>(env->GetArrayLength(challenge));
    std::uint64_t challenge_tag = 0;
    if (challenge_size > 0) {
        auto* bytes = env->GetByteArrayElements(challenge, nullptr);
        if (bytes != nullptr) {
            challenge_tag = fnv1a(reinterpret_cast<std::uint8_t*>(bytes), challenge_size);
            env->ReleaseByteArrayElements(challenge, bytes, JNI_ABORT);
        }
    }

    std::ostringstream json;
    json << '{'
         << "\"implementation_version\":\"0.1.0\"," 
         << "\"boundary\":\"application-process-procfs-and-mount-namespace\"," 
         << "\"challenge_length\":" << challenge_size << ','
         << "\"challenge_tag_fnv1a64\":\"" << std::hex << std::setw(16) << std::setfill('0') << challenge_tag << std::dec << "\"," 
         << "\"maps_records\":" << maps.records << ','
         << "\"maps_executable_records\":" << maps.executable << ','
         << "\"maps_anonymous_executable_records\":" << maps.anonymous_executable << ','
         << "\"maps_deleted_backing_records\":" << maps.deleted_backing << ','
         << "\"maps_suspicious_name_hits\":" << maps.suspicious_name_hits << ','
         << "\"maps_malformed_records\":" << maps.malformed << ','
         << "\"maps_truncated\":" << bool_json(maps.truncated) << ','
         << "\"mount_records\":" << mounts.records << ','
         << "\"mount_overlay_records\":" << mounts.overlay << ','
         << "\"mount_proc_records\":" << mounts.proc << ','
         << "\"mount_tmpfs_records\":" << mounts.tmpfs << ','
         << "\"mount_malformed_records\":" << mounts.malformed << ','
         << "\"mount_truncated\":" << bool_json(mounts.truncated) << ','
         << "\"tracer_pid_present\":" << bool_json(status.tracer_present) << ','
         << "\"tracer_pid\":" << status.tracer_pid << ','
         << "\"no_new_privs_present\":" << bool_json(status.no_new_privs_present) << ','
         << "\"no_new_privs\":" << status.no_new_privs << ','
         << "\"seccomp_present\":" << bool_json(status.seccomp_present) << ','
         << "\"seccomp\":" << status.seccomp << ','
         << "\"status_malformed_records\":" << status.malformed << ','
         << "\"limitations\":["
         << "\"self-view-only\","
         << "\"higher-privilege-software-may-mediate-procfs-or-mount-views\","
         << "\"summary-values-are-not-a-compromise-verdict\","
         << "\"challenge-tag-is-correlation-only-not-a-cryptographic-binding\""
         << "]}";
    const auto result = json.str();
    return env->NewStringUTF(result.c_str());
}

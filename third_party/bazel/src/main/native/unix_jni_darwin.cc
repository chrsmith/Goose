// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "unix_jni.h"

#include <assert.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>

#include <string>

using std::string;

// See unix_jni.h.
string ErrorMessage(int error_number) {
  char buf[1024] = "";
  if (strerror_r(error_number, buf, sizeof buf) < 0) {
    snprintf(buf, sizeof buf, "strerror_r(%d): errno %d", error_number, errno);
  }

  return string(buf);
}

int portable_fstatat(int dirfd, char *name, struct stat *statbuf, int flags) {
  errno = ENOSYS;
  return -1;
}

int StatSeconds(const struct stat &statbuf, StatTimes t) {
  switch (t) {
    case STAT_ATIME:
      return statbuf.st_atime;
    case STAT_CTIME:
      return statbuf.st_ctime;
    case STAT_MTIME:
      return statbuf.st_mtime;
    default:
      CHECK(false);
  }
}

int StatNanoSeconds(const struct stat &statbuf, StatTimes t) {
  switch (t) {
    case STAT_ATIME:
      return statbuf.st_atimespec.tv_nsec;
    case STAT_CTIME:
      return statbuf.st_ctimespec.tv_nsec;
    case STAT_MTIME:
      return statbuf.st_mtimespec.tv_nsec;
    default:
      CHECK(false);
  }
}

ssize_t portable_getxattr(const char *path, const char *name, void *value,
                          size_t size) {
  errno = ENOSYS;
  return -1;
}

ssize_t portable_lgetxattr(const char *path, const char *name, void *value,
                           size_t size) {
  errno = ENOSYS;
  return -1;
}
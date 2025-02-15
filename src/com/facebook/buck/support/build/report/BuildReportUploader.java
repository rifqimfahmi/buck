/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.support.build.report;

import com.facebook.buck.core.exceptions.BuckUncheckedExecutionException;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.util.json.ObjectMappers;
import com.facebook.buck.util.versioncontrol.FullVersionControlStats;
import com.facebook.buck.util.versioncontrol.VersionControlCommandFailedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * BuildReportUploader does the heavy lifting of {@link BuildReportUpload}. It encodes and uploads
 * the given {@link FullBuildReport}
 */
public class BuildReportUploader {
  private static final Logger LOG = Logger.get(BuildReportUploader.class);

  private RequestUploader requestUploader;

  /**
   * @param endpointUrl the endpoint to upload to
   * @param timeout in milliseconds for http client
   */
  BuildReportUploader(URL endpointUrl, long timeout, BuildId buildId) {
    this.requestUploader = new RequestUploader(endpointUrl, timeout, buildId);
  }

  /**
   * Upload a build report to the server
   *
   * @param report the report that's being uploaded
   * @return the endpoints' response
   * @throws IOException an exception related with the http connection or json mapping
   */
  public UploadResponse uploadReport(FullBuildReport report) throws IOException {

    report
        .versionControlStats()
        .flatMap(FullVersionControlStats::getDiff)
        .ifPresent(
            diffSupplier -> {
              try {
                InputStream inputStream = diffSupplier.get();
                uploadDiffFile(inputStream, requestUploader.getBuildId());
              } catch (VersionControlCommandFailedException | InterruptedException e) {
                LOG.error(e);
              } catch (IOException e) {
                LOG.warn(e, "Error when executing http call for diff file");
              }
            });

    return uploadFullBuildReport(report);
  }

  @VisibleForTesting
  UploadResponse uploadFullBuildReport(FullBuildReport report) throws IOException {

    String encodedResponse;
    try {
      encodedResponse = ObjectMappers.WRITER.writeValueAsString(report);
    } catch (JsonProcessingException e) {
      throw new BuckUncheckedExecutionException(e, "Uploading build report");
    }

    RequestBody formBody = new FormBody.Builder().add("data", encodedResponse).build();

    return requestUploader.uploadRequest(formBody);
  }

  /**
   * @param inputStream that has an input stream containing the output of `hg export` command see
   *     {@link com.facebook.buck.util.versioncontrol.HgCmdLineInterface} for more info.
   * @param buildId of the current build
   * @return the endpoints' response
   * @throws IOException when an error occurs while executing the http call
   */
  public UploadResponse uploadDiffFile(InputStream inputStream, String buildId) throws IOException {

    String diffContent = CharStreams.toString(new InputStreamReader(inputStream));

    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "trace_file",
                "hg_diff_" + buildId,
                RequestBody.create(MediaType.parse("text/plain"), diffContent))
            .build();

    return requestUploader.uploadRequest(
        requestBody, ImmutableMap.of("trace_file_kind", "diff_file"));
  }
}

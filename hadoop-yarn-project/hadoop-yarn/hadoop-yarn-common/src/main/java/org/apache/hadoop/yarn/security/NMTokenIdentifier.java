/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.security;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability.Evolving;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.proto.YarnProtos.ApplicationAttemptIdProto;
import org.apache.hadoop.yarn.proto.YarnProtos.ApplicationIdProto;
import org.apache.hadoop.yarn.proto.YarnProtos.NodeIdProto;
import org.apache.hadoop.yarn.proto.YarnSecurityTokenProtos.NMTokenIdentifierProto;

@Public
@Evolving
public class NMTokenIdentifier extends TokenIdentifier {

  private static Log LOG = LogFactory.getLog(NMTokenIdentifier.class);

  public static final Text KIND = new Text("NMToken");
  
  private final NMTokenIdentifierProto.Builder protoBuilder = NMTokenIdentifierProto.newBuilder();
  
  private ApplicationAttemptId appAttemptId;
  private NodeId nodeId;
  private String appSubmitter;
  private int keyId;

  public NMTokenIdentifier(ApplicationAttemptId appAttemptId, NodeId nodeId,
      String applicationSubmitter, int masterKeyId) {
    this.appAttemptId = appAttemptId;
    this.nodeId = nodeId;
    this.appSubmitter = applicationSubmitter;
    this.keyId = masterKeyId;
  }
  
  /**
   * Default constructor needed by RPC/Secret manager
   */
  public NMTokenIdentifier() {
  }

  public ApplicationAttemptId getApplicationAttemptId() {
    return appAttemptId;
  }
  
  public NodeId getNodeId() {
    return nodeId;
  }
  
  public String getApplicationSubmitter() {
    return appSubmitter;
  }
  
  public int getKeyId() {
    return keyId;
  }
  
  @Override
  public void write(DataOutput out) throws IOException {
    LOG.debug("Writing NMTokenIdentifier to RPC layer: " + this);
    ApplicationId applicationId = appAttemptId.getApplicationId();
    out.writeLong(applicationId.getClusterTimestamp());
    out.writeInt(applicationId.getId());
    out.writeInt(appAttemptId.getAttemptId());
    out.writeUTF(this.nodeId.toString());
    out.writeUTF(this.appSubmitter);
    out.writeInt(this.keyId);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    //make a copy of the input stream so that it can be read many times
    byte[] data = IOUtils.readFullyToByteArray(in);
    try {
      //hdp 2 way
      DataInput di = new DataInputStream(new ByteArrayInputStream(data));
      appAttemptId =
              ApplicationAttemptId.newInstance(
                      ApplicationId.newInstance(di.readLong(), di.readInt()),
                      di.readInt());
      String[] hostAddr = di.readUTF().split(":");
      nodeId = NodeId.newInstance(hostAddr[0], Integer.parseInt(hostAddr[1]));
      appSubmitter = di.readUTF();
      keyId = di.readInt();
    } catch(IOException e) {
      //HDF 3 way using proto
      protoBuilder.mergeFrom(data);

      ApplicationAttemptIdProto applicationAttemptIdProto = protoBuilder.getAppAttemptId();
      ApplicationIdProto applicationIdProto = applicationAttemptIdProto.getApplicationId();

      appAttemptId = ApplicationAttemptId.newInstance(
              ApplicationId.newInstance(applicationIdProto.getClusterTimestamp(), applicationIdProto.getId()),
              applicationAttemptIdProto.getAttemptId()
      );

      NodeIdProto nodeIdProto = protoBuilder.getNodeId();
      nodeId = NodeId.newInstance(nodeIdProto.getHost(), nodeIdProto.getPort());

      appSubmitter = protoBuilder.getAppSubmitter();
      keyId = protoBuilder.getKeyId();
    }
  }

  @Override
  public Text getKind() {
    return KIND;
  }

  @Override
  public UserGroupInformation getUser() {
    return UserGroupInformation.createRemoteUser(appAttemptId.toString());
  }
}

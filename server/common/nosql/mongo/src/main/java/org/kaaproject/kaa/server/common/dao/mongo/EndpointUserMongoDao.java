/*
 * Copyright 2014 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaaproject.kaa.server.common.dao.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.UUID;

import org.kaaproject.kaa.common.dto.EndpointUserDto;
import org.kaaproject.kaa.server.common.dao.impl.EndpointUserDao;
import org.kaaproject.kaa.server.common.dao.mongo.model.MongoEndpointUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class EndpointUserMongoDao extends AbstractMongoDao<MongoEndpointUser> implements EndpointUserDao<MongoEndpointUser> {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointUserMongoDao.class);

    @Override
    protected String getCollectionName() {
        return MongoEndpointUser.COLLECTION_NAME;
    }

    @Override
    protected Class<MongoEndpointUser> getDocumentClass() {
        return MongoEndpointUser.class;
    }

    @Override
    public MongoEndpointUser findByExternalIdAndTenantId(String externalId, String tenantId) {
        LOG.debug("Find user by external uid [{}] and tenant id [{}] ", externalId, tenantId);
        return findOne(query(where(EXTERNAL_ID).is(externalId).and(TENANT_ID).is(tenantId)));
    }

    @Override
    public void removeByExternalIdAndTenantId(String externalId, String tenantId) {
        LOG.debug("Remove user by external uid [{}] and tenant id [{}] ", externalId, tenantId);
        remove(query(where(EXTERNAL_ID).is(externalId).and(TENANT_ID).is(tenantId)));
    }

    @Override
    public String generateAccessToken(String externalUid, String tenantId) {
        MongoEndpointUser endpointUser = findByExternalIdAndTenantId(externalUid, tenantId);
        String accessToken = UUID.randomUUID().toString();
        endpointUser.setAccessToken(accessToken);
        save(endpointUser);
        return accessToken;
    }

    @Override
    public boolean checkAccessToken(String tenantId, String externalId, String accessToken) {
        MongoEndpointUser endpointUser = findByExternalIdAndTenantId(externalId, tenantId);
        if(endpointUser == null){
            LOG.debug("Can't find user with external id {}", externalId);
            return false;
        }else{
            String realAccessToken = endpointUser.getAccessToken();
            return realAccessToken != null && realAccessToken.equals(accessToken);
        }
    }

    @Override
    public MongoEndpointUser save(EndpointUserDto dto) {
        return save(new MongoEndpointUser(dto));
    }
}
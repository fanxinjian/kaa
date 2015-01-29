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

package org.kaaproject.kaa.server.common.dao.service;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.kaaproject.kaa.server.common.dao.impl.DaoUtil.convertDtoList;
import static org.kaaproject.kaa.server.common.dao.impl.DaoUtil.getDto;
import static org.kaaproject.kaa.server.common.dao.impl.DaoUtil.getStringFromFile;
import static org.kaaproject.kaa.server.common.dao.service.Validator.isValidSqlId;
import static org.kaaproject.kaa.server.common.dao.service.Validator.isValidSqlObject;

import java.util.List;

import org.apache.commons.lang.RandomStringUtils;
import org.kaaproject.kaa.common.Constants;
import org.kaaproject.kaa.common.dto.ApplicationDto;
import org.kaaproject.kaa.common.dto.ConfigurationDto;
import org.kaaproject.kaa.common.dto.ConfigurationSchemaDto;
import org.kaaproject.kaa.common.dto.EndpointGroupDto;
import org.kaaproject.kaa.common.dto.KaaAuthorityDto;
import org.kaaproject.kaa.common.dto.NotificationSchemaDto;
import org.kaaproject.kaa.common.dto.NotificationTypeDto;
import org.kaaproject.kaa.common.dto.ProfileFilterDto;
import org.kaaproject.kaa.common.dto.ProfileSchemaDto;
import org.kaaproject.kaa.common.dto.logs.LogSchemaDto;
import org.kaaproject.kaa.server.common.core.schema.DataSchema;
import org.kaaproject.kaa.server.common.core.schema.KaaSchemaFactoryImpl;
import org.kaaproject.kaa.server.common.dao.ApplicationService;
import org.kaaproject.kaa.server.common.dao.ConfigurationService;
import org.kaaproject.kaa.server.common.dao.EndpointService;
import org.kaaproject.kaa.server.common.dao.LogSchemaService;
import org.kaaproject.kaa.server.common.dao.NotificationService;
import org.kaaproject.kaa.server.common.dao.ProfileService;
import org.kaaproject.kaa.server.common.dao.TopicService;
import org.kaaproject.kaa.server.common.dao.exception.IncorrectParameterException;
import org.kaaproject.kaa.server.common.dao.impl.ApplicationDao;
import org.kaaproject.kaa.server.common.dao.impl.UserDao;
import org.kaaproject.kaa.server.common.dao.model.sql.Application;
import org.kaaproject.kaa.server.common.dao.model.sql.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationServiceImpl implements ApplicationService {

    public static final String DUMMY_ENDPOINT_USER_VERIFIER = "dummyEndpointUserVerifier";
    public static final String MONGODB_ENDPOINT_LOG_APPENDER = "mongoDBLogAppender";
    public static final String FILESYSTEM_ENDPOINT_LOG_APPENDER = "fileSystemLogAppender";

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationServiceImpl.class);

    private static final String GROUP_ALL = "All";

    private static final String DEFAULT_PROFILE_SCHEMA_FILE = "/default_profile_schema.json";
    private static final String DEFAULT_CONFIGURATION_SCHEMA_FILE = "/default_configuration_schema.json";
    private static final String DEFAULT_NOTIFICATION_SCHEMA_FILE = "/default_notification_schema.json";
    private static final String DEFAULT_LOG_SCHEMA_FILE = "/default_log_schema.json";
    private static final String DEFAULT_SCHEMA_NAME = "Generated";
    

    @Autowired
    private ApplicationDao<Application> applicationDao;

    @Autowired
    private EndpointService endpointService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LogSchemaService logSchemaService;

    @Autowired
    private TopicService topicService;

    @Autowired
    private UserDao<User> userDao;

    @Override
    public List<ApplicationDto> findAppsByTenantId(String tenantId) {
        List<ApplicationDto> applicationDto;
        if (isValidSqlId(tenantId)) {
            LOG.debug("Find applications by tenant id [{}]", tenantId);
            applicationDto = convertDtoList(applicationDao.findByTenantId(tenantId));
        } else {
            throw new IncorrectParameterException("Incorrect tenant id: " + tenantId);
        }
        return applicationDto;
    }

    @Override
    public void removeAppsByTenantId(String tenantId) {
        LOG.debug("Remove applications by tenant id [{}]", tenantId);
        if (isValidSqlId(tenantId)) {
            List<ApplicationDto> applications = findAppsByTenantId(tenantId);
            for (ApplicationDto application : applications) {
                removeAppById(application.getId());
            }
        }
    }

    @Override
    public ApplicationDto findAppById(String id) {
        ApplicationDto applicationDto = null;
        if (isValidSqlId(id)) {
            applicationDto = getDto(applicationDao.findById(id));
        }
        return applicationDto;
    }

    @Override
    public void removeAppById(String id) {
        if (isValidSqlId(id)) {
            removeCascadeApplication(id);
        }
    }

    @Override
    public ApplicationDto findAppByApplicationToken(String applicationToken) {
        ApplicationDto applicationDto = null;
        if (isNotBlank(applicationToken)) {
            applicationDto = getDto(applicationDao.findByApplicationToken(applicationToken));
        }
        return applicationDto;
    }

    @Override
    public ApplicationDto saveApp(ApplicationDto applicationDto) {
        ApplicationDto appDto = null;
        if (isValidSqlObject(applicationDto)) {
            if(isNotBlank(applicationDto.getId())) {
                LOG.debug("Update application with id [{}]", applicationDto.getId());
                Application checkApplication = applicationDao.findByNameAndTenantId(applicationDto.getName(), applicationDto.getTenantId());
                if (checkApplication == null || checkApplication.getId().equals(applicationDto.getId())) {
                    appDto = getDto(applicationDao.save(new Application(applicationDto)));
                } else {
                    throw new IncorrectParameterException("Can't save application with same name within one tenant");
                }
                return appDto;
            }
            String appToken = RandomStringUtils.randomNumeric(Constants.APP_TOKEN_SIZE);
            applicationDto.setApplicationToken(appToken);

            if(applicationDto.getLogAppendersNames() == null || applicationDto.getLogAppendersNames().isEmpty()){
                applicationDto.setLogAppendersNames(MONGODB_ENDPOINT_LOG_APPENDER + "," + FILESYSTEM_ENDPOINT_LOG_APPENDER);
            }

            Application application = new Application(applicationDto);
            appDto = getDto(applicationDao.save(application));

            if(appDto != null) {
                String appId = appDto.getId();
                List<User> users = userDao.findByTenantIdAndAuthority(appDto.getTenantId(), KaaAuthorityDto.TENANT_ADMIN.name());
                String createdUsername = null;
                if (!users.isEmpty()) {
                    createdUsername = users.get(0).getUsername();
                }
                LOG.debug("Saved application with id [{}]", appId);
                EndpointGroupDto groupDto = createDefaultGroup(appId, createdUsername);

                if(groupDto != null) {
                    String groupId = groupDto.getId();
                    LOG.debug("Saved endpoint group with id [{}]", groupId);
                    ProfileFilterDto filter = createDefaultProfileWithSchema(appId, groupId, createdUsername);
                    ConfigurationDto configuration = createDefaultConfigurationWithSchema(appId, groupId, createdUsername);
                    if (filter == null || configuration == null) {
                        LOG.warn("Got error during creation application. Deleted application with id [{}]", appId);
                        removeCascadeApplication(appId);
                    }
                    LOG.debug("Creating default notification schema");
                    createDefaultNotificationSchema(appId, createdUsername);
                    LOG.debug("Creating default log schema");
                    createDefaultLogSchema(appId, createdUsername);
                } else {
                    LOG.warn("Cant save default group for application with id [{}]", appId);
                    removeCascadeApplication(appId);
                }
            }
            LOG.debug("Inserted new application with");
        }
        return appDto;
    }

    private EndpointGroupDto createDefaultGroup(String appId, String createdUsername) {
        EndpointGroupDto endpointGroup = new EndpointGroupDto();
        endpointGroup.setName(GROUP_ALL);
        endpointGroup.setCreatedUsername(createdUsername);
        endpointGroup.setApplicationId(appId);
        return endpointService.saveEndpointGroup(endpointGroup);
    }

    private ProfileFilterDto createDefaultProfileWithSchema(String appId, String groupId, String createdUsername) {
        ProfileSchemaDto profileSchemaDto = new ProfileSchemaDto();
        profileSchemaDto.setApplicationId(appId);
        DataSchema schema = new KaaSchemaFactoryImpl().createDataSchema(getStringFromFile(DEFAULT_PROFILE_SCHEMA_FILE, ApplicationServiceImpl.class));
        if (!schema.isEmpty()) {
            profileSchemaDto.setSchema(schema.getRawSchema());
        } else {
            throw new RuntimeException("Can't read default profile schema."); //NOSONAR
        }
        profileSchemaDto.setName(DEFAULT_SCHEMA_NAME);
        profileSchemaDto.setCreatedUsername(createdUsername);
        profileSchemaDto = profileService.saveProfileSchema(profileSchemaDto);

        if (profileSchemaDto != null) {
            return profileService.findLatestFilterBySchemaIdAndGroupId(profileSchemaDto.getId(), groupId);
        } else {
            throw new RuntimeException("Can't save default profile schema " + profileSchemaDto); //NOSONAR
        }
    }

    private ConfigurationDto createDefaultConfigurationWithSchema(String appId, String groupId, String createdUsername) {
        ConfigurationSchemaDto schema = new ConfigurationSchemaDto();
        schema.setApplicationId(appId);
        DataSchema confSchema = new KaaSchemaFactoryImpl().createDataSchema(getStringFromFile(DEFAULT_CONFIGURATION_SCHEMA_FILE, ApplicationServiceImpl.class));
        if (!confSchema.isEmpty()) {
            schema.setSchema(confSchema.getRawSchema());
        } else {
            throw new RuntimeException("Can't read default configuration schema."); //NOSONAR
        }
        schema.setName(DEFAULT_SCHEMA_NAME);
        schema.setCreatedUsername(createdUsername);
        ConfigurationSchemaDto savedSchema = configurationService.saveConfSchema(schema, groupId);
        ConfigurationDto config = configurationService.findConfigurationByAppIdAndVersion(savedSchema.getApplicationId(), savedSchema.getMajorVersion());
        if (config == null) {
            throw new RuntimeException("Can't find default configuration by schema id " + savedSchema.getId()); //NOSONAR
        } else {
            return config;
        }
    }

    private NotificationSchemaDto createDefaultNotificationSchema(String appId, String createdUsername) {
        NotificationSchemaDto schema = new NotificationSchemaDto();
        schema.setApplicationId(appId);
        DataSchema defSchema =  new KaaSchemaFactoryImpl().createDataSchema(getStringFromFile(DEFAULT_NOTIFICATION_SCHEMA_FILE, ApplicationServiceImpl.class));
        if (!defSchema.isEmpty()) {
            schema.setSchema(defSchema.getRawSchema());
        } else {
            throw new RuntimeException("Can't read default notification schema."); //NOSONAR
        }
        schema.setType(NotificationTypeDto.USER);
        schema.setName(DEFAULT_SCHEMA_NAME);
        schema.setCreatedUsername(createdUsername);

        return notificationService.saveNotificationSchema(schema);
    }

    private LogSchemaDto createDefaultLogSchema(String appId, String createdUsername) {
        LogSchemaDto schema = new LogSchemaDto();
        schema.setApplicationId(appId);
        DataSchema defSchema =  new KaaSchemaFactoryImpl().createDataSchema(getStringFromFile(DEFAULT_LOG_SCHEMA_FILE, ApplicationServiceImpl.class));
        if (!defSchema.isEmpty()) {
            schema.setSchema(defSchema.getRawSchema());
        } else {
            throw new RuntimeException("Can't read default log schema."); //NOSONAR
        }
        schema.setName(DEFAULT_SCHEMA_NAME);
        schema.setCreatedUsername(createdUsername);
        return logSchemaService.saveLogSchema(schema);
    }

    private void removeCascadeApplication(String id) {
//        configurationService.removeConfSchemasByAppId(id);
//        endpointService.removeEndpointGroupByAppId(id);
//        topicService.removeTopicsByAppId(id);
//        endpointService.removeEndpointProfileByAppId(id);
//        logSchemaService.removeLogSchemasByAppId(id);
        applicationDao.removeById(id);
//        notificationService.removeNotificationSchemasByAppId(id);
    }

}

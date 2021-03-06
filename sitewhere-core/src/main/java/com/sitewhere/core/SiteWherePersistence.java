/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.encoding.MessageDigestPasswordEncoder;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;

import com.sitewhere.rest.model.common.MetadataProvider;
import com.sitewhere.rest.model.common.MetadataProviderEntity;
import com.sitewhere.rest.model.device.Device;
import com.sitewhere.rest.model.device.DeviceAssignment;
import com.sitewhere.rest.model.device.DeviceAssignmentState;
import com.sitewhere.rest.model.device.DeviceElementMapping;
import com.sitewhere.rest.model.device.DeviceSpecification;
import com.sitewhere.rest.model.device.Site;
import com.sitewhere.rest.model.device.SiteMapData;
import com.sitewhere.rest.model.device.Zone;
import com.sitewhere.rest.model.device.command.DeviceCommand;
import com.sitewhere.rest.model.device.element.DeviceElementSchema;
import com.sitewhere.rest.model.device.event.DeviceAlert;
import com.sitewhere.rest.model.device.event.DeviceCommandInvocation;
import com.sitewhere.rest.model.device.event.DeviceCommandResponse;
import com.sitewhere.rest.model.device.event.DeviceEvent;
import com.sitewhere.rest.model.device.event.DeviceEventBatchResponse;
import com.sitewhere.rest.model.device.event.DeviceLocation;
import com.sitewhere.rest.model.device.event.DeviceMeasurement;
import com.sitewhere.rest.model.device.event.DeviceMeasurements;
import com.sitewhere.rest.model.device.event.DeviceStateChange;
import com.sitewhere.rest.model.device.group.DeviceGroup;
import com.sitewhere.rest.model.device.group.DeviceGroupElement;
import com.sitewhere.rest.model.device.request.DeviceCreateRequest;
import com.sitewhere.rest.model.user.GrantedAuthority;
import com.sitewhere.rest.model.user.User;
import com.sitewhere.security.LoginManager;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.common.ILocation;
import com.sitewhere.spi.device.DeviceAssignmentStatus;
import com.sitewhere.spi.device.DeviceAssignmentType;
import com.sitewhere.spi.device.DeviceContainerPolicy;
import com.sitewhere.spi.device.DeviceStatus;
import com.sitewhere.spi.device.IDevice;
import com.sitewhere.spi.device.IDeviceAssignment;
import com.sitewhere.spi.device.IDeviceElementMapping;
import com.sitewhere.spi.device.IDeviceManagement;
import com.sitewhere.spi.device.IDeviceSpecification;
import com.sitewhere.spi.device.command.ICommandParameter;
import com.sitewhere.spi.device.command.IDeviceCommand;
import com.sitewhere.spi.device.element.IDeviceElementSchema;
import com.sitewhere.spi.device.event.AlertLevel;
import com.sitewhere.spi.device.event.AlertSource;
import com.sitewhere.spi.device.event.CommandStatus;
import com.sitewhere.spi.device.event.IDeviceAlert;
import com.sitewhere.spi.device.event.IDeviceEventBatch;
import com.sitewhere.spi.device.event.IDeviceLocation;
import com.sitewhere.spi.device.event.IDeviceMeasurement;
import com.sitewhere.spi.device.event.IDeviceMeasurements;
import com.sitewhere.spi.device.event.request.IDeviceAlertCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceCommandInvocationCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceCommandResponseCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceEventCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceLocationCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceMeasurementsCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceStateChangeCreateRequest;
import com.sitewhere.spi.device.request.IDeviceAssignmentCreateRequest;
import com.sitewhere.spi.device.request.IDeviceCommandCreateRequest;
import com.sitewhere.spi.device.request.IDeviceCreateRequest;
import com.sitewhere.spi.device.request.IDeviceGroupCreateRequest;
import com.sitewhere.spi.device.request.IDeviceGroupElementCreateRequest;
import com.sitewhere.spi.device.request.IDeviceSpecificationCreateRequest;
import com.sitewhere.spi.device.request.ISiteCreateRequest;
import com.sitewhere.spi.device.request.IZoneCreateRequest;
import com.sitewhere.spi.device.util.DeviceSpecificationUtils;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.user.request.IGrantedAuthorityCreateRequest;
import com.sitewhere.spi.user.request.IUserCreateRequest;

/**
 * Common methods needed by device service provider implementations.
 * 
 * @author Derek
 */
public class SiteWherePersistence {

	/** Password encoder */
	private static MessageDigestPasswordEncoder passwordEncoder = new ShaPasswordEncoder();

	/**
	 * Initialize entity fields.
	 * 
	 * @param entity
	 * @throws SiteWhereException
	 */
	public static void initializeEntityMetadata(MetadataProviderEntity entity) throws SiteWhereException {
		entity.setCreatedDate(new Date());
		entity.setCreatedBy(LoginManager.getCurrentlyLoggedInUser().getUsername());
		entity.setDeleted(false);
	}

	/**
	 * Set updated fields.
	 * 
	 * @param entity
	 * @throws SiteWhereException
	 */
	public static void setUpdatedEntityMetadata(MetadataProviderEntity entity) throws SiteWhereException {
		entity.setUpdatedDate(new Date());
		entity.setUpdatedBy(LoginManager.getCurrentlyLoggedInUser().getUsername());
	}

	/**
	 * Common logic for creating new device specification and populating it from request.
	 * 
	 * @param request
	 * @param token
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceSpecification deviceSpecificationCreateLogic(
			IDeviceSpecificationCreateRequest request, String token) throws SiteWhereException {
		DeviceSpecification spec = new DeviceSpecification();

		// Unique token is required.
		if (token == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		spec.setToken(token);

		// Name is required.
		if (request.getName() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		spec.setName(request.getName());

		// Asset module id is required.
		if (request.getAssetModuleId() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		spec.setAssetModuleId(request.getAssetModuleId());

		// Asset id is required.
		if (request.getAssetId() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		spec.setAssetId(request.getAssetId());

		// Container policy is required.
		if (request.getContainerPolicy() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		spec.setContainerPolicy(request.getContainerPolicy());

		// If composite container policy and no device element schema, create an empty
		// schema.
		if (request.getContainerPolicy() == DeviceContainerPolicy.Composite) {
			IDeviceElementSchema schema = request.getDeviceElementSchema();
			if (schema == null) {
				schema = new DeviceElementSchema();
			}
			spec.setDeviceElementSchema((DeviceElementSchema) schema);
		}

		MetadataProvider.copy(request, spec);
		SiteWherePersistence.initializeEntityMetadata(spec);
		return spec;
	}

	/**
	 * Common logic for updating a device specification from request.
	 * 
	 * @param request
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void deviceSpecificationUpdateLogic(IDeviceSpecificationCreateRequest request,
			DeviceSpecification target) throws SiteWhereException {
		if (request.getName() != null) {
			target.setName(request.getName());
		}
		if (request.getContainerPolicy() != null) {
			target.setContainerPolicy(request.getContainerPolicy());
		}
		// Only allow schema to be set if new or existing container policy is 'composite'.
		if (target.getContainerPolicy() == DeviceContainerPolicy.Composite) {
			if (request.getContainerPolicy() == DeviceContainerPolicy.Standalone) {
				target.setDeviceElementSchema(null);
			} else {
				IDeviceElementSchema schema = request.getDeviceElementSchema();
				if (schema == null) {
					schema = new DeviceElementSchema();
				}
				target.setDeviceElementSchema((DeviceElementSchema) schema);
			}
		}
		if (request.getAssetModuleId() != null) {
			target.setAssetModuleId(request.getAssetModuleId());
		}
		if (request.getAssetId() != null) {
			target.setAssetId(request.getAssetId());
		}
		if ((request.getMetadata() != null) && (request.getMetadata().size() > 0)) {
			target.getMetadata().clear();
			MetadataProvider.copy(request, target);
		}
		SiteWherePersistence.setUpdatedEntityMetadata(target);
	}

	/**
	 * Common logic for creating new device command and populating it from request.
	 * 
	 * @param request
	 * @param token
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceCommand deviceCommandCreateLogic(IDeviceSpecification spec,
			IDeviceCommandCreateRequest request, String token, List<IDeviceCommand> existing)
			throws SiteWhereException {
		DeviceCommand command = new DeviceCommand();

		// Token is required.
		if (token == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		command.setToken(token);

		// Name is required.
		if (request.getName() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		command.setName(request.getName());

		command.setSpecificationToken(spec.getToken());
		command.setNamespace(request.getNamespace());
		command.setDescription(request.getDescription());
		command.getParameters().addAll(request.getParameters());

		checkDuplicateCommand(command, existing);

		MetadataProvider.copy(request, command);
		SiteWherePersistence.initializeEntityMetadata(command);
		return command;
	}

	/**
	 * Checks whether a command is already in the given list (same name and namespace).
	 * 
	 * @param command
	 * @param existing
	 * @throws SiteWhereException
	 */
	protected static void checkDuplicateCommand(DeviceCommand command, List<IDeviceCommand> existing)
			throws SiteWhereException {
		boolean duplicate = false;
		for (IDeviceCommand current : existing) {
			if (current.getName().equals(command.getName())) {
				if (current.getNamespace() == null) {
					if (command.getNamespace() == null) {
						duplicate = true;
						break;
					}
				} else if (current.getNamespace().equals(command.getNamespace())) {
					duplicate = true;
					break;
				}
			}
		}
		if (duplicate) {
			throw new SiteWhereSystemException(ErrorCode.DeviceCommandExists, ErrorLevel.ERROR);
		}
	}

	/**
	 * Common logic for updating a device command from request.
	 * 
	 * @param request
	 * @param target
	 * @param existing
	 * @throws SiteWhereException
	 */
	public static void deviceCommandUpdateLogic(IDeviceCommandCreateRequest request, DeviceCommand target,
			List<IDeviceCommand> existing) throws SiteWhereException {
		if (request.getName() != null) {
			target.setName(request.getName());
		}
		if (request.getNamespace() != null) {
			target.setNamespace(request.getNamespace());
		}

		// Make sure the update will not result in a duplicate.
		checkDuplicateCommand(target, existing);

		if (request.getDescription() != null) {
			target.setDescription(request.getDescription());
		}
		if (request.getParameters() != null) {
			target.getParameters().clear();
			target.getParameters().addAll(request.getParameters());
		}
		if (request.getMetadata() != null) {
			target.getMetadata().clear();
			MetadataProvider.copy(request, target);
		}
		SiteWherePersistence.setUpdatedEntityMetadata(target);
	}

	/**
	 * Common logic for creating new device object and populating it from request.
	 * 
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static Device deviceCreateLogic(IDeviceCreateRequest request) throws SiteWhereException {
		Device device = new Device();
		device.setHardwareId(request.getHardwareId());
		device.setSpecificationToken(request.getSpecificationToken());
		device.setComments(request.getComments());
		device.setStatus(DeviceStatus.Ok);

		MetadataProvider.copy(request, device);
		SiteWherePersistence.initializeEntityMetadata(device);
		return device;
	}

	/**
	 * Common logic for updating a device object from request.
	 * 
	 * @param request
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void deviceUpdateLogic(IDeviceCreateRequest request, Device target)
			throws SiteWhereException {
		// Can not update the hardware id on a device.
		if ((request.getHardwareId() != null) && (!request.getHardwareId().equals(target.getHardwareId()))) {
			throw new SiteWhereSystemException(ErrorCode.DeviceHardwareIdCanNotBeChanged, ErrorLevel.ERROR,
					HttpServletResponse.SC_BAD_REQUEST);
		}
		if (request.getSpecificationToken() != null) {
			target.setSpecificationToken(request.getSpecificationToken());
		}
		if (request.isRemoveParentHardwareId()) {
			target.setParentHardwareId(null);
		}
		if (request.getParentHardwareId() != null) {
			target.setParentHardwareId(request.getParentHardwareId());
		}
		if (request.getDeviceElementMappings() != null) {
			List<DeviceElementMapping> mappings = new ArrayList<DeviceElementMapping>();
			for (IDeviceElementMapping mapping : request.getDeviceElementMappings()) {
				mappings.add(DeviceElementMapping.copy(mapping));
			}
			target.setDeviceElementMappings(mappings);
		}
		if (request.getComments() != null) {
			target.setComments(request.getComments());
		}
		if (request.getStatus() != null) {
			target.setStatus(request.getStatus());
		}
		if ((request.getMetadata() != null) && (request.getMetadata().size() > 0)) {
			target.getMetadata().clear();
			MetadataProvider.copy(request, target);
		}
		SiteWherePersistence.setUpdatedEntityMetadata(target);
	}

	/**
	 * Encapsulates logic for creating a new {@link IDeviceElementMapping}.
	 * 
	 * @param management
	 * @param hardwareId
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static IDevice deviceElementMappingCreateLogic(IDeviceManagement management, String hardwareId,
			IDeviceElementMapping request) throws SiteWhereException {
		IDevice device = management.getDeviceByHardwareId(hardwareId);
		if (device == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidHardwareId, ErrorLevel.ERROR);
		}
		IDevice mapped = management.getDeviceByHardwareId(request.getHardwareId());
		if (mapped == null) {
			throw new SiteWhereException("Device referenced by mapping does not exist.");
		}

		// Check whether target device is already parented to another device.
		if (mapped.getParentHardwareId() != null) {
			throw new SiteWhereSystemException(ErrorCode.DeviceParentMappingExists, ErrorLevel.ERROR);
		}

		// Verify that requested path is valid on device specification.
		IDeviceSpecification specification =
				management.getDeviceSpecificationByToken(device.getSpecificationToken());
		DeviceSpecificationUtils.getDeviceSlotByPath(specification, request.getDeviceElementSchemaPath());

		// Verify that there is not an existing mapping for the path.
		List<IDeviceElementMapping> existing = device.getDeviceElementMappings();
		List<DeviceElementMapping> newMappings = new ArrayList<DeviceElementMapping>();
		for (IDeviceElementMapping mapping : existing) {
			if (mapping.getDeviceElementSchemaPath().equals(request.getDeviceElementSchemaPath())) {
				throw new SiteWhereSystemException(ErrorCode.DeviceElementMappingExists, ErrorLevel.ERROR);
			}
			newMappings.add(DeviceElementMapping.copy(mapping));
		}
		DeviceElementMapping newMapping = DeviceElementMapping.copy(request);
		newMappings.add(newMapping);

		// Add parent backreference for nested device.
		DeviceCreateRequest nested = new DeviceCreateRequest();
		nested.setParentHardwareId(hardwareId);
		management.updateDevice(mapped.getHardwareId(), nested);

		// Update device with new mapping.
		DeviceCreateRequest update = new DeviceCreateRequest();
		update.setDeviceElementMappings(newMappings);
		IDevice updated = management.updateDevice(hardwareId, update);
		return updated;
	}

	/**
	 * Encapsulates logic for deleting an {@link IDeviceElementMapping}.
	 * 
	 * @param management
	 * @param hardwareId
	 * @param path
	 * @return
	 * @throws SiteWhereException
	 */
	public static IDevice deviceElementMappingDeleteLogic(IDeviceManagement management, String hardwareId,
			String path) throws SiteWhereException {
		IDevice device = management.getDeviceByHardwareId(hardwareId);
		if (device == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidHardwareId, ErrorLevel.ERROR);
		}

		// Verify that mapping exists and build list without deleted mapping.
		List<IDeviceElementMapping> existing = device.getDeviceElementMappings();
		List<DeviceElementMapping> newMappings = new ArrayList<DeviceElementMapping>();
		IDeviceElementMapping match = null;
		for (IDeviceElementMapping mapping : existing) {
			if (mapping.getDeviceElementSchemaPath().equals(path)) {
				match = mapping;
			} else {
				newMappings.add(DeviceElementMapping.copy(mapping));
			}
		}
		if (match == null) {
			throw new SiteWhereSystemException(ErrorCode.DeviceElementMappingDoesNotExist, ErrorLevel.ERROR);
		}

		// Remove parent reference from nested device.
		IDevice mapped = management.getDeviceByHardwareId(match.getHardwareId());
		if (mapped != null) {
			DeviceCreateRequest nested = new DeviceCreateRequest();
			nested.setRemoveParentHardwareId(true);
			management.updateDevice(mapped.getHardwareId(), nested);
		}

		// Update device with new mappings.
		DeviceCreateRequest update = new DeviceCreateRequest();
		update.setDeviceElementMappings(newMappings);
		IDevice updated = management.updateDevice(hardwareId, update);
		return updated;
	}

	/**
	 * Common logic for creating new site object and populating it from request.
	 * 
	 * @param source
	 * @param uuid
	 * @return
	 * @throws SiteWhereException
	 */
	public static Site siteCreateLogic(ISiteCreateRequest source, String uuid) throws SiteWhereException {
		Site site = new Site();
		site.setName(source.getName());
		site.setDescription(source.getDescription());
		site.setImageUrl(source.getImageUrl());
		site.setToken(uuid);
		site.setMap(SiteMapData.copy(source.getMap()));

		SiteWherePersistence.initializeEntityMetadata(site);
		MetadataProvider.copy(source, site);
		return site;
	}

	/**
	 * Common logic for copying data from site update request to existing site.
	 * 
	 * @param source
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void siteUpdateLogic(ISiteCreateRequest source, Site target) throws SiteWhereException {
		target.setName(source.getName());
		target.setDescription(source.getDescription());
		target.setImageUrl(source.getImageUrl());
		target.clearMetadata();
		target.setMap(SiteMapData.copy(source.getMap()));

		MetadataProvider.copy(source, target);
		SiteWherePersistence.setUpdatedEntityMetadata(target);
	}

	/**
	 * Common logic for creating a device assignment from a request.
	 * 
	 * @param source
	 * @param siteToken
	 * @param uuid
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceAssignment deviceAssignmentCreateLogic(IDeviceAssignmentCreateRequest source,
			String siteToken, String uuid) throws SiteWhereException {
		DeviceAssignment newAssignment = new DeviceAssignment();

		if (uuid == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		newAssignment.setToken(uuid);

		if (source.getSiteToken() == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidSiteToken, ErrorLevel.ERROR);
		}
		newAssignment.setSiteToken(source.getSiteToken());

		if (source.getDeviceHardwareId() == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidHardwareId, ErrorLevel.ERROR);
		}
		newAssignment.setDeviceHardwareId(source.getDeviceHardwareId());

		if (source.getAssignmentType() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		newAssignment.setAssignmentType(source.getAssignmentType());

		if (source.getAssignmentType() == DeviceAssignmentType.Associated) {
			if (source.getAssetModuleId() == null) {
				throw new SiteWhereSystemException(ErrorCode.InvalidAssetReferenceId, ErrorLevel.ERROR);
			}
			newAssignment.setAssetModuleId(source.getAssetModuleId());

			if (source.getAssetId() == null) {
				throw new SiteWhereSystemException(ErrorCode.InvalidAssetReferenceId, ErrorLevel.ERROR);
			}
			newAssignment.setAssetId(source.getAssetId());
		}

		newAssignment.setActiveDate(new Date());
		newAssignment.setStatus(DeviceAssignmentStatus.Active);

		SiteWherePersistence.initializeEntityMetadata(newAssignment);
		MetadataProvider.copy(source, newAssignment);

		return newAssignment;
	}

	/**
	 * Executes logic to process a batch of device events.
	 * 
	 * @param assignmentToken
	 * @param batch
	 * @param management
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceEventBatchResponse deviceEventBatchLogic(String assignmentToken,
			IDeviceEventBatch batch, IDeviceManagement management) throws SiteWhereException {
		DeviceEventBatchResponse response = new DeviceEventBatchResponse();
		for (IDeviceMeasurementsCreateRequest measurements : batch.getMeasurements()) {
			response.getCreatedMeasurements().add(
					management.addDeviceMeasurements(assignmentToken, measurements));
		}
		for (IDeviceLocationCreateRequest location : batch.getLocations()) {
			response.getCreatedLocations().add(management.addDeviceLocation(assignmentToken, location));
		}
		for (IDeviceAlertCreateRequest alert : batch.getAlerts()) {
			response.getCreatedAlerts().add(management.addDeviceAlert(assignmentToken, alert));
		}
		return response;
	}

	/**
	 * Common creation logic for all device events.
	 * 
	 * @param request
	 * @param assignment
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void deviceEventCreateLogic(IDeviceEventCreateRequest request,
			IDeviceAssignment assignment, DeviceEvent target) throws SiteWhereException {
		target.setSiteToken(assignment.getSiteToken());
		target.setDeviceAssignmentToken(assignment.getToken());
		target.setAssignmentType(assignment.getAssignmentType());
		target.setAssetModuleId(assignment.getAssetModuleId());
		target.setAssetId(assignment.getAssetId());
		if (request.getEventDate() != null) {
			target.setEventDate(request.getEventDate());
		} else {
			target.setEventDate(new Date());
		}
		target.setReceivedDate(new Date());
		MetadataProvider.copy(request, target);
	}

	/**
	 * Common logic for creating {@link DeviceMeasurements} from
	 * {@link IDeviceMeasurementsCreateRequest}.
	 * 
	 * @param request
	 * @param assignment
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceMeasurements deviceMeasurementsCreateLogic(IDeviceMeasurementsCreateRequest request,
			IDeviceAssignment assignment) throws SiteWhereException {
		DeviceMeasurements measurements = new DeviceMeasurements();
		deviceEventCreateLogic(request, assignment, measurements);
		for (String key : request.getMeasurements().keySet()) {
			measurements.addOrReplaceMeasurement(key, request.getMeasurement(key));
		}
		return measurements;
	}

	/**
	 * Common logic for creating {@link DeviceLocation} from
	 * {@link IDeviceLocationCreateRequest}.
	 * 
	 * @param assignment
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceLocation deviceLocationCreateLogic(IDeviceAssignment assignment,
			IDeviceLocationCreateRequest request) throws SiteWhereException {
		DeviceLocation location = new DeviceLocation();
		deviceEventCreateLogic(request, assignment, location);
		location.setLatitude(request.getLatitude());
		location.setLongitude(request.getLongitude());
		location.setElevation(request.getElevation());
		return location;
	}

	/**
	 * Common logic for creating {@link DeviceAlert} from
	 * {@link IDeviceAlertCreateRequest}.
	 * 
	 * @param assignment
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceAlert deviceAlertCreateLogic(IDeviceAssignment assignment,
			IDeviceAlertCreateRequest request) throws SiteWhereException {
		DeviceAlert alert = new DeviceAlert();
		deviceEventCreateLogic(request, assignment, alert);
		alert.setSource(AlertSource.Device);
		if (request.getLevel() != null) {
			alert.setLevel(request.getLevel());
		} else {
			alert.setLevel(AlertLevel.Info);
		}
		alert.setType(request.getType());
		alert.setMessage(request.getMessage());
		return alert;
	}

	/**
	 * Common logic for creating {@link DeviceCommandInvocation} from an
	 * {@link IDeviceCommandInvocationCreateRequest}.
	 * 
	 * @param assignment
	 * @param command
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceCommandInvocation deviceCommandInvocationCreateLogic(IDeviceAssignment assignment,
			IDeviceCommand command, IDeviceCommandInvocationCreateRequest request) throws SiteWhereException {
		if (request.getInitiator() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		if (request.getTarget() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		for (ICommandParameter parameter : command.getParameters()) {
			checkData(parameter, request.getParameterValues());
		}
		DeviceCommandInvocation ci = new DeviceCommandInvocation();
		deviceEventCreateLogic(request, assignment, ci);
		ci.setCommandToken(command.getToken());
		ci.setInitiator(request.getInitiator());
		ci.setInitiatorId(request.getInitiatorId());
		ci.setTarget(request.getTarget());
		ci.setTargetId(request.getTargetId());
		ci.setParameterValues(request.getParameterValues());
		if (request.getStatus() != null) {
			ci.setStatus(request.getStatus());
		} else {
			ci.setStatus(CommandStatus.Pending);
		}
		return ci;
	}

	/**
	 * Verify that data supplied for command parameters is valid.
	 * 
	 * @param parameter
	 * @param values
	 * @throws SiteWhereException
	 */
	protected static void checkData(ICommandParameter parameter, Map<String, String> values)
			throws SiteWhereException {
		// Make sure required fields are passed.
		if (parameter.isRequired()) {
			if (values.get(parameter.getName()) == null) {
				throw new SiteWhereException("Required parameter '" + parameter.getName() + "' is missing.");
			}
		}
		// If no value, do not try to validate.
		String value = values.get(parameter.getName());
		if (value == null) {
			return;
		}
		switch (parameter.getType()) {
		case Fixed32:
		case Fixed64:
		case Int32:
		case Int64:
		case SFixed32:
		case SFixed64:
		case SInt32:
		case SInt64:
		case UInt32:
		case UInt64: {
			try {
				Long.parseLong(value);
			} catch (NumberFormatException e) {
				throw new SiteWhereException("Parameter '" + parameter.getName() + "' must be numeric.");
			}
		}
		case Float: {
			try {
				Float.parseFloat(value);
			} catch (NumberFormatException e) {
				throw new SiteWhereException("Parameter '" + parameter.getName() + "' must be a float.");
			}
		}
		case Double: {
			try {
				Double.parseDouble(value);
			} catch (NumberFormatException e) {
				throw new SiteWhereException("Parameter '" + parameter.getName() + "' must be a double.");
			}
		}
		default:
		}
	}

	/**
	 * Common logic for creating a {@link DeviceCommandResponse} from an
	 * {@link IDeviceCommandResponseCreateRequest}.
	 * 
	 * @param assignment
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceCommandResponse deviceCommandResponseCreateLogic(IDeviceAssignment assignment,
			IDeviceCommandResponseCreateRequest request) throws SiteWhereException {
		if (request.getOriginatingEventId() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		DeviceCommandResponse response = new DeviceCommandResponse();
		deviceEventCreateLogic(request, assignment, response);
		response.setOriginatingEventId(request.getOriginatingEventId());
		response.setResponseEventId(request.getResponseEventId());
		response.setResponse(request.getResponse());
		return response;
	}

	/**
	 * Common logic for creating a {@link DeviceStateChange} from an
	 * {@link IDeviceStateChangeCreateRequest}.
	 * 
	 * @param assignment
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceStateChange deviceStateChangeCreateLogic(IDeviceAssignment assignment,
			IDeviceStateChangeCreateRequest request) throws SiteWhereException {
		if (request.getCategory() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		if (request.getType() == null) {
			throw new SiteWhereSystemException(ErrorCode.IncompleteData, ErrorLevel.ERROR);
		}
		DeviceStateChange state = new DeviceStateChange();
		deviceEventCreateLogic(request, assignment, state);
		state.setCategory(request.getCategory());
		state.setType(request.getType());
		state.setPreviousState(request.getPreviousState());
		state.setNewState(request.getNewState());
		if (request.getData() != null) {
			state.getData().putAll(request.getData());
		}
		return state;
	}

	/**
	 * Gets a copy of the existing state or creates a new state.
	 * 
	 * @param assignment
	 * @return
	 */
	protected static DeviceAssignmentState assureState(IDeviceAssignment assignment) {
		if (assignment.getState() == null) {
			return new DeviceAssignmentState();
		}
		return DeviceAssignmentState.copy(assignment.getState());
	}

	/**
	 * Update latest device location if necessary.
	 * 
	 * @param assignment
	 * @param location
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceAssignmentState assignmentStateLocationUpdateLogic(IDeviceAssignment assignment,
			IDeviceLocation location) throws SiteWhereException {
		DeviceAssignmentState existing = assureState(assignment);
		existing.setLastInteractionDate(new Date());

		if ((existing.getLastLocation() == null)
				|| (location.getEventDate().after(existing.getLastLocation().getEventDate()))) {
			existing.setLastLocation(DeviceLocation.copy(location));
		}
		return existing;
	}

	/**
	 * Update latest device measurements if necessary.
	 * 
	 * @param assignment
	 * @param measurements
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceAssignmentState assignmentStateMeasurementsUpdateLogic(IDeviceAssignment assignment,
			IDeviceMeasurements measurements) throws SiteWhereException {
		DeviceAssignmentState existing = assureState(assignment);
		existing.setLastInteractionDate(new Date());

		Map<String, IDeviceMeasurement> measurementsById = new HashMap<String, IDeviceMeasurement>();
		if (existing.getLatestMeasurements() != null) {
			for (IDeviceMeasurement m : existing.getLatestMeasurements()) {
				measurementsById.put(m.getName(), m);
			}
		}
		for (String key : measurements.getMeasurements().keySet()) {
			IDeviceMeasurement em = measurementsById.get(key);
			if ((em == null) || (em.getEventDate().before(measurements.getEventDate()))) {
				Double value = measurements.getMeasurement(key);
				DeviceMeasurement newMeasurement = new DeviceMeasurement();
				DeviceEvent.copy(measurements, newMeasurement);
				newMeasurement.setName(key);
				newMeasurement.setValue(value);
				measurementsById.put(key, newMeasurement);
			}
		}
		existing.getLatestMeasurements().clear();
		for (IDeviceMeasurement m : measurementsById.values()) {
			existing.getLatestMeasurements().add(m);
		}
		return existing;
	}

	/**
	 * Update device alerts if necessary.
	 * 
	 * @param assignment
	 * @param alert
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceAssignmentState assignmentStateAlertUpdateLogic(IDeviceAssignment assignment,
			IDeviceAlert alert) throws SiteWhereException {
		DeviceAssignmentState existing = assureState(assignment);
		existing.setLastInteractionDate(new Date());

		Map<String, IDeviceAlert> alertsById = new HashMap<String, IDeviceAlert>();
		if ((existing != null) && (existing.getLatestAlerts() != null)) {
			for (IDeviceAlert a : existing.getLatestAlerts()) {
				alertsById.put(a.getType(), a);
			}
		}
		IDeviceAlert ea = alertsById.get(alert.getType());
		if ((ea == null) || (ea.getEventDate().before(alert.getEventDate()))) {
			DeviceAlert newAlert = DeviceAlert.copy(alert);
			alertsById.put(newAlert.getType(), newAlert);
		}
		existing.getLatestAlerts().clear();
		for (IDeviceAlert a : alertsById.values()) {
			existing.getLatestAlerts().add(a);
		}
		return existing;
	}

	/**
	 * Common logic for creating a zone based on an incoming request.
	 * 
	 * @param source
	 * @param siteToken
	 * @param uuid
	 * @return
	 * @throws SiteWhereException
	 */
	public static Zone zoneCreateLogic(IZoneCreateRequest source, String siteToken, String uuid)
			throws SiteWhereException {
		Zone zone = new Zone();
		zone.setToken(uuid);
		zone.setSiteToken(siteToken);
		zone.setName(source.getName());
		zone.setBorderColor(source.getBorderColor());
		zone.setFillColor(source.getFillColor());
		zone.setOpacity(source.getOpacity());

		SiteWherePersistence.initializeEntityMetadata(zone);
		MetadataProvider.copy(source, zone);

		for (ILocation coordinate : source.getCoordinates()) {
			zone.getCoordinates().add(coordinate);
		}
		return zone;
	}

	/**
	 * Common code for copying information from an update request to an existing zone.
	 * 
	 * @param source
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void zoneUpdateLogic(IZoneCreateRequest source, Zone target) throws SiteWhereException {
		target.setName(source.getName());
		target.setBorderColor(source.getBorderColor());
		target.setFillColor(source.getFillColor());
		target.setOpacity(source.getOpacity());

		target.getCoordinates().clear();
		for (ILocation coordinate : source.getCoordinates()) {
			target.getCoordinates().add(coordinate);
		}

		SiteWherePersistence.setUpdatedEntityMetadata(target);
		MetadataProvider.copy(source, target);
	}

	/**
	 * Common logic for creating a device group based on an incoming request.
	 * 
	 * @param source
	 * @param uuid
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceGroup deviceGroupCreateLogic(IDeviceGroupCreateRequest source, String uuid)
			throws SiteWhereException {
		DeviceGroup group = new DeviceGroup();
		group.setToken(uuid);
		group.setName(source.getName());
		group.setDescription(source.getDescription());
		if (source.getRoles() != null) {
			group.getRoles().addAll(source.getRoles());
		}

		SiteWherePersistence.initializeEntityMetadata(group);
		MetadataProvider.copy(source, group);
		return group;
	}

	/**
	 * Common logic for updating an existing device group.
	 * 
	 * @param source
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void deviceGroupUpdateLogic(IDeviceGroupCreateRequest source, DeviceGroup target)
			throws SiteWhereException {
		target.setName(source.getName());
		target.setDescription(source.getDescription());

		if (source.getRoles() != null) {
			target.getRoles().clear();
			target.getRoles().addAll(source.getRoles());
		}

		SiteWherePersistence.setUpdatedEntityMetadata(target);
		MetadataProvider.copy(source, target);
	}

	/**
	 * Common logic for creating a new device group element.
	 * 
	 * @param source
	 * @param groupToken
	 * @param index
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceGroupElement deviceGroupElementCreateLogic(IDeviceGroupElementCreateRequest source,
			String groupToken, long index) throws SiteWhereException {
		DeviceGroupElement element = new DeviceGroupElement();
		element.setGroupToken(groupToken);
		element.setIndex(index);
		element.setType(source.getType());
		element.setElementId(source.getElementId());
		element.setRoles(source.getRoles());
		return element;
	}

	/**
	 * Common logic for creating a user based on an incoming request.
	 * 
	 * @param source
	 * @return
	 * @throws SiteWhereException
	 */
	public static User userCreateLogic(IUserCreateRequest source) throws SiteWhereException {
		User user = new User();
		user.setUsername(source.getUsername());
		user.setHashedPassword(passwordEncoder.encodePassword(source.getPassword(), null));
		user.setFirstName(source.getFirstName());
		user.setLastName(source.getLastName());
		user.setLastLogin(null);
		user.setStatus(source.getStatus());
		user.setAuthorities(source.getAuthorities());

		MetadataProvider.copy(source, user);
		SiteWherePersistence.initializeEntityMetadata(user);
		return user;
	}

	/**
	 * Common code for copying information from an update request to an existing user.
	 * 
	 * @param source
	 * @param target
	 * @throws SiteWhereException
	 */
	public static void userUpdateLogic(IUserCreateRequest source, User target) throws SiteWhereException {
		if (source.getUsername() != null) {
			target.setUsername(source.getUsername());
		}
		if (source.getPassword() != null) {
			target.setHashedPassword(passwordEncoder.encodePassword(source.getPassword(), null));
		}
		if (source.getFirstName() != null) {
			target.setFirstName(source.getFirstName());
		}
		if (source.getLastName() != null) {
			target.setLastName(source.getLastName());
		}
		if (source.getStatus() != null) {
			target.setStatus(source.getStatus());
		}
		if (source.getAuthorities() != null) {
			target.setAuthorities(source.getAuthorities());
		}
		if ((source.getMetadata() != null) && (source.getMetadata().size() > 0)) {
			target.getMetadata().clear();
			MetadataProvider.copy(source, target);
		}
		SiteWherePersistence.setUpdatedEntityMetadata(target);
	}

	/**
	 * Common logic for creating a granted authority based on an incoming request.
	 * 
	 * @param source
	 * @return
	 * @throws SiteWhereException
	 */
	public static GrantedAuthority grantedAuthorityCreateLogic(IGrantedAuthorityCreateRequest source)
			throws SiteWhereException {
		GrantedAuthority auth = new GrantedAuthority();
		auth.setAuthority(source.getAuthority());
		auth.setDescription(source.getDescription());
		return auth;
	}

	/**
	 * Common logic for encoding a plaintext password.
	 * 
	 * @param plaintext
	 * @return
	 */
	public static String encodePassoword(String plaintext) {
		return passwordEncoder.encodePassword(plaintext, null);
	}
}
package com.hackathon.calendlylite.service;

import com.hackathon.calendlylite.dto.CreateHostRequest;
import com.hackathon.calendlylite.dto.CreateSlotRequest;
import com.hackathon.calendlylite.entity.AvailabilitySlot;
import com.hackathon.calendlylite.entity.Host;
import com.hackathon.calendlylite.enums.SlotStatus;
import com.hackathon.calendlylite.exception.ApiException;
import com.hackathon.calendlylite.repository.AvailabilitySlotRepository;
import com.hackathon.calendlylite.repository.HostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HostService {

    private static final Logger log = LoggerFactory.getLogger(HostService.class);

    private final HostRepository hostRepository;
    private final AvailabilitySlotRepository slotRepository;

    public HostService(HostRepository hostRepository, AvailabilitySlotRepository slotRepository) {
        this.hostRepository = hostRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional
    public Host createHost(CreateHostRequest request) {
        if (hostRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Host with email " + request.getEmail() + " already exists", HttpStatus.CONFLICT);
        }
        Host host = new Host(request.getName(), request.getEmail());
        Host saved = hostRepository.save(host);
        log.info("Created host: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    public Host getHost(Long hostId) {
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new ApiException("Host not found with id: " + hostId, HttpStatus.NOT_FOUND));
    }

    public List<Host> getAllHosts() {
        return hostRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = "slots", key = "#hostId")
    public AvailabilitySlot addSlot(Long hostId, CreateSlotRequest request) {
        Host host = getHost(hostId);

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new ApiException("End time must be after start time", HttpStatus.BAD_REQUEST);
        }
        if (slotRepository.existsOverlappingSlot(hostId, request.getStartTime(), request.getEndTime(), SlotStatus.CANCELLED)) {
            throw new ApiException("This time slot overlaps with an existing slot", HttpStatus.CONFLICT);
        }

        AvailabilitySlot slot = AvailabilitySlot.builder()
                .host(host)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SlotStatus.OPEN)
                .build();

        AvailabilitySlot saved = slotRepository.save(slot);
        log.info("Host {} added slot: {} to {} (id={})", hostId, saved.getStartTime(), saved.getEndTime(), saved.getId());
        return saved;
    }

    /**
     * Cached with Redis (TTL 5 min). Invalidated on add/book/cancel via @CacheEvict.
     */
    @Cacheable(value = "slots", key = "#hostId")
    public List<AvailabilitySlot> getOpenSlots(Long hostId) {
        getHost(hostId);
        log.debug("Cache MISS for host {} slots — fetching from DB", hostId);
        return slotRepository.findByHost_IdAndStatus(hostId, SlotStatus.OPEN);
    }
}

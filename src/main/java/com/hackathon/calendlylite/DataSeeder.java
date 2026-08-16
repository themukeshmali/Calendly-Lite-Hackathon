package com.hackathon.calendlylite;

import com.hackathon.calendlylite.entity.AvailabilitySlot;
import com.hackathon.calendlylite.entity.Host;
import com.hackathon.calendlylite.enums.SlotStatus;
import com.hackathon.calendlylite.repository.AvailabilitySlotRepository;
import com.hackathon.calendlylite.repository.HostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Seeds 2 demo hosts and 5 open slots on first startup (skips if data exists).
 * Run once: if any hosts exist in the DB, this is a no-op.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final HostRepository hostRepository;
    private final AvailabilitySlotRepository slotRepository;

    public DataSeeder(HostRepository hostRepository, AvailabilitySlotRepository slotRepository) {
        this.hostRepository = hostRepository;
        this.slotRepository = slotRepository;
    }

    @Override
    public void run(String... args) {
        if (hostRepository.count() > 0) {
            log.info("DataSeeder: Skipping — data already exists");
            return;
        }

        log.info("DataSeeder: Inserting demo data...");

        Host priya = hostRepository.save(new Host("Dr. Priya Sharma", "priya@clinic.com"));
        Host alex  = hostRepository.save(new Host("Alex Mehta", "alex@consult.com"));

        // Truncate to the top of the hour tomorrow — safe and explicit ordering
        LocalDateTime base = LocalDateTime.now()
                .plusDays(1)
                .truncatedTo(ChronoUnit.HOURS);

        saveSlot(priya, base.withHour(9),  base.withHour(10));
        saveSlot(priya, base.withHour(11), base.withHour(12));
        saveSlot(priya, base.withHour(14), base.withHour(15));
        saveSlot(alex,  base.withHour(10), base.withHour(10).plusMinutes(30));
        saveSlot(alex,  base.withHour(15), base.withHour(16));

        log.info("DataSeeder: Created 2 hosts and 5 slots. Visit http://localhost:8080/swagger-ui.html");
    }

    private void saveSlot(Host host, LocalDateTime start, LocalDateTime end) {
        slotRepository.save(AvailabilitySlot.builder()
                .host(host)
                .startTime(start)
                .endTime(end)
                .status(SlotStatus.OPEN)
                .build());
    }
}

package com.anistream.domain.usecase

import com.anistream.core.common.Result
import com.anistream.domain.model.ScheduleEntry
import com.anistream.domain.repository.AnidbRepository

class GetScheduleUseCase(private val repository: AnidbRepository) {
    suspend operator fun invoke(): Result<List<ScheduleEntry>> {
        return repository.getSchedule()
    }
}

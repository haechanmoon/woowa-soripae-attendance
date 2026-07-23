package com.woowasoripae.attendance.web.member;

import com.woowasoripae.attendance.domain.member.MemberService;
import com.woowasoripae.attendance.web.member.dto.FineSummaryResponse;
import com.woowasoripae.attendance.web.member.dto.MemberCreateRequest;
import com.woowasoripae.attendance.web.member.dto.MemberDetailResponse;
import com.woowasoripae.attendance.web.member.dto.MemberSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 부원 목록 조회 API. */
    @GetMapping("/api/members")
    public List<MemberSummaryResponse> getMembers() {
        return memberService.getAllMembers();
    }

    /** 부원/임원 등록 API. */
    @PostMapping("/api/members")
    public ResponseEntity<MemberSummaryResponse> createMember(@Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
    }

    /** 부원별 누적 벌금 조회 API. */
    @GetMapping("/api/members/{memberId}/fines")
    public FineSummaryResponse getFines(@PathVariable Long memberId) {
        return memberService.getFineSummary(memberId);
    }

    @GetMapping("/api/members/{memberId}")
    public MemberDetailResponse getMemberDetail(@PathVariable Long memberId) {
        return memberService.getMemberDetail(memberId);
    }
}

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

    /**
     * 홈 탭 "송금 완료 알림" 버튼. MVP 스텁: 회원 존재만 검증하고 202를 반환한다.
     * 실제 임원진에게 알림을 전달하는 채널(슬랙/카톡 웹훅 등)은 아직 연결되어 있지 않으므로,
     * 이 응답만으로는 임원진이 아무것도 보지 못한다 — 알림 채널 결정 후 별도로 연동 필요.
     */
    @PostMapping("/api/members/{memberId}/remittance-notifications")
    public ResponseEntity<Void> notifyRemittance(@PathVariable Long memberId) {
        memberService.assertMemberExists(memberId);
        return ResponseEntity.accepted().build();
    }
}

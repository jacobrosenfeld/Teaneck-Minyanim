import React, { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SymbolView } from 'expo-symbols';

import { capture } from '@/analytics';
import { isMobileFeedbackConfigured } from '@/api/client';
import { useOrganizations, useSubmitFeedback } from '@/api/hooks';
import type { FeedbackCategory } from '@/api/types';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { buildMobileFeedbackMetadata } from '@/utils/feedbackMetadata';

const MAX_MESSAGE_LENGTH = 5000;

type SymbolName = React.ComponentProps<typeof SymbolView>['name'];

const FEEDBACK_CATEGORIES: {
  value: FeedbackCategory;
  title: string;
  subtitle: string;
  icon: SymbolName;
}[] = [
  {
    value: 'MINYAN_SCHEDULE',
    title: 'Minyan Time',
    subtitle: 'Schedule or data issue',
    icon: 'calendar',
  },
  {
    value: 'APP_FUNCTIONALITY',
    title: 'App Issue',
    subtitle: 'Bug or feature problem',
    icon: 'wrench.and.screwdriver',
  },
];

function isValidEmail(email: string): boolean {
  if (!email.trim()) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

export default function FeedbackScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const submitFeedback = useSubmitFeedback();
  const { data: organizations } = useOrganizations();

  const [category, setCategory] = useState<FeedbackCategory>('MINYAN_SCHEDULE');
  const [message, setMessage] = useState('');
  const [email, setEmail] = useState('');
  const [submittedIssueUrl, setSubmittedIssueUrl] = useState<string | null>(null);
  const [submittedIssueNumber, setSubmittedIssueNumber] = useState<number | null>(null);

  const configured = isMobileFeedbackConfigured();
  const trimmedMessage = message.trim();
  const trimmedEmail = email.trim();
  const emailValid = isValidEmail(email);
  const messageTooLong = message.length > MAX_MESSAGE_LENGTH;
  const canSubmit = configured
    && trimmedMessage.length > 0
    && !messageTooLong
    && emailValid
    && !submitFeedback.isPending;

  const helperText = useMemo(() => {
    if (messageTooLong) {
      return `${message.length.toLocaleString()} / ${MAX_MESSAGE_LENGTH.toLocaleString()}`;
    }
    if (!emailValid) {
      return 'Enter a valid email address or leave it blank.';
    }
    if (submitFeedback.isError) {
      return submitFeedback.error instanceof Error
        ? submitFeedback.error.message
        : 'Could not send feedback.';
    }
    return '';
  }, [emailValid, message.length, messageTooLong, submitFeedback.error, submitFeedback.isError]);

  const handleCategoryChange = useCallback((nextCategory: FeedbackCategory) => {
    setCategory(nextCategory);
    capture('feedback_category_selected', {
      screen: 'feedback',
      category: nextCategory,
    });
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!canSubmit) return;

    setSubmittedIssueUrl(null);
    setSubmittedIssueNumber(null);
    capture('feedback_submit_attempt', {
      screen: 'feedback',
      category,
      has_email: trimmedEmail.length > 0,
    });

    try {
      const response = await submitFeedback.mutateAsync({
        category,
        message: trimmedMessage,
        email: trimmedEmail || undefined,
        metadata: buildMobileFeedbackMetadata(organizations),
      });
      setSubmittedIssueUrl(response.githubIssueUrl);
      setSubmittedIssueNumber(response.githubIssueNumber);
      setMessage('');
      setEmail('');
      capture('feedback_submit_success', {
        screen: 'feedback',
        category,
        feedback_id: response.feedbackId,
        issue_number: response.githubIssueNumber,
      });
    } catch (error) {
      capture('feedback_submit_failure', {
        screen: 'feedback',
        category,
        error_message: error instanceof Error ? error.message : 'unknown',
      });
    }
  }, [canSubmit, category, organizations, submitFeedback, trimmedEmail, trimmedMessage]);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>
      <KeyboardAvoidingView
        style={styles.keyboard}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}>
          <View style={[styles.header, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View>
              <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
              <Text style={[styles.title, { color: colors.text }]}>Feedback</Text>
            </View>
            <View style={[styles.headerIcon, { backgroundColor: colors.tint }]}>
              <SymbolView name="bubble.left.and.bubble.right.fill" tintColor="#FFFFFF" size={22} />
            </View>
          </View>

          {!configured ? (
            <View style={[styles.notice, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={[styles.noticeTitle, { color: colors.text }]}>Feedback unavailable</Text>
              <Text style={[styles.noticeText, { color: colors.textSecondary }]}>
                This app build is missing feedback configuration.
              </Text>
            </View>
          ) : null}

          {submittedIssueUrl ? (
            <View style={[styles.success, { backgroundColor: '#E9F8EF', borderColor: '#B7E4C7' }]}>
              <View style={[styles.successIcon, { backgroundColor: '#166534' }]}>
                <SymbolView name="checkmark" tintColor="#FFFFFF" size={18} />
              </View>
              <View style={styles.successBody}>
                <Text style={styles.successTitle}>Feedback sent</Text>
                <Text style={styles.successText}>
                  {submittedIssueNumber ? `GitHub issue #${submittedIssueNumber}` : 'GitHub issue created'}
                </Text>
              </View>
              <Pressable onPress={() => Linking.openURL(submittedIssueUrl)} style={styles.successLink}>
                <Text style={styles.successLinkText}>Open</Text>
              </Pressable>
            </View>
          ) : null}

          <View style={styles.section}>
            <Text style={[styles.label, { color: colors.text }]}>Type</Text>
            <View style={styles.categoryGrid}>
              {FEEDBACK_CATEGORIES.map((option) => (
                <CategoryButton
                  key={option.value}
                  option={option}
                  selected={category === option.value}
                  colors={colors}
                  onPress={() => handleCategoryChange(option.value)}
                />
              ))}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={[styles.label, { color: colors.text }]}>Message</Text>
            <TextInput
              value={message}
              onChangeText={setMessage}
              placeholder="What should we fix?"
              placeholderTextColor={colors.textTertiary}
              multiline
              textAlignVertical="top"
              maxLength={MAX_MESSAGE_LENGTH + 1}
              style={[
                styles.messageInput,
                {
                  color: colors.text,
                  backgroundColor: colors.card,
                  borderColor: messageTooLong ? '#DC2626' : colors.border,
                },
              ]}
            />
          </View>

          <View style={styles.section}>
            <Text style={[styles.label, { color: colors.text }]}>Email</Text>
            <TextInput
              value={email}
              onChangeText={setEmail}
              placeholder="Optional"
              placeholderTextColor={colors.textTertiary}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              textContentType="emailAddress"
              style={[
                styles.emailInput,
                {
                  color: colors.text,
                  backgroundColor: colors.card,
                  borderColor: emailValid ? colors.border : '#DC2626',
                },
              ]}
            />
          </View>

          {helperText ? (
            <Text style={[styles.helperText, { color: submitFeedback.isError || messageTooLong || !emailValid ? '#DC2626' : colors.textSecondary }]}>
              {helperText}
            </Text>
          ) : null}

          <Pressable
            disabled={!canSubmit}
            onPress={handleSubmit}
            style={({ pressed }) => [
              styles.submitButton,
              {
                backgroundColor: canSubmit ? colors.tint : colors.border,
                opacity: pressed && canSubmit ? 0.88 : 1,
              },
            ]}>
            {submitFeedback.isPending ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <>
                <SymbolView name="paperplane.fill" tintColor="#FFFFFF" size={18} />
                <Text style={styles.submitButtonText}>Send Feedback</Text>
              </>
            )}
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function CategoryButton({
  option,
  selected,
  colors,
  onPress,
}: {
  option: (typeof FEEDBACK_CATEGORIES)[number];
  selected: boolean;
  colors: typeof Colors.light;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.categoryButton,
        {
          backgroundColor: selected ? colors.tint : colors.card,
          borderColor: selected ? colors.tint : colors.border,
          opacity: pressed ? 0.9 : 1,
        },
      ]}>
      <SymbolView
        name={option.icon}
        tintColor={selected ? '#FFFFFF' : colors.tint}
        size={19}
      />
      <View style={styles.categoryText}>
        <Text style={[styles.categoryTitle, { color: selected ? '#FFFFFF' : colors.text }]}>
          {option.title}
        </Text>
        <Text style={[styles.categorySubtitle, { color: selected ? 'rgba(255,255,255,0.78)' : colors.textSecondary }]}>
          {option.subtitle}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  keyboard: { flex: 1 },
  content: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 116,
  },
  header: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 18,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.08, shadowRadius: 10 },
      android: { elevation: 3 },
    }),
  },
  siteName: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0,
    textTransform: 'uppercase',
    marginBottom: 3,
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    letterSpacing: 0,
  },
  headerIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  notice: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    marginBottom: 18,
  },
  noticeTitle: {
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 3,
  },
  noticeText: {
    fontSize: 13,
    lineHeight: 18,
  },
  success: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 12,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  successIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
  },
  successBody: { flex: 1 },
  successTitle: {
    color: '#14532D',
    fontSize: 14,
    fontWeight: '800',
  },
  successText: {
    color: '#166534',
    fontSize: 12,
    marginTop: 2,
  },
  successLink: {
    paddingHorizontal: 10,
    paddingVertical: 7,
  },
  successLinkText: {
    color: '#166534',
    fontSize: 13,
    fontWeight: '800',
  },
  section: {
    marginBottom: 16,
  },
  label: {
    fontSize: 13,
    fontWeight: '800',
    marginBottom: 8,
  },
  categoryGrid: {
    gap: 10,
  },
  categoryButton: {
    minHeight: 70,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  categoryText: {
    flex: 1,
  },
  categoryTitle: {
    fontSize: 15,
    fontWeight: '800',
    letterSpacing: 0,
  },
  categorySubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  messageInput: {
    minHeight: 150,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    lineHeight: 22,
  },
  emailInput: {
    height: 52,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 14,
    fontSize: 16,
  },
  helperText: {
    fontSize: 12,
    lineHeight: 17,
    marginTop: -4,
    marginBottom: 14,
  },
  submitButton: {
    minHeight: 54,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '800',
    letterSpacing: 0,
  },
});

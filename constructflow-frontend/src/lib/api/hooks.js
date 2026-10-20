"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, errorMessage } from "./client";

/**
 * Paginated list bound to a Spring endpoint. Filters, page and sort are part of the query key, so
 * changing any of them fetches the right page and previous results stay visible meanwhile.
 */
export function usePagedQuery(key, path, { filters = {}, size = 20, initialSort, enabled = true } = {}) {
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(initialSort);
  const query = useQuery({
    queryKey: [...key, { ...filters, page, size, sort }],
    queryFn: ({ signal }) => api.get(path, { ...filters, page, size, sort }, { signal }),
    placeholderData: keepPreviousData,
    enabled,
  });
  return {
    ...query,
    page,
    setPage,
    sort,
    setSort: (value) => {
      setSort(value);
      setPage(0);
    },
    rows: query.data?.content ?? [],
    size,
  };
}

/**
 * Mutation with consistent UX: success toast only after the API confirms, error toast with the
 * server's message otherwise, and invalidation of the listed query keys.
 */
export function useApiMutation(mutationFn, { invalidate = [], success, onSuccess, onError, silentError = false } = {}) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async (data, variables) => {
      await Promise.all(invalidate.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
      if (success) toast.success(typeof success === "function" ? success(data, variables) : success);
      onSuccess?.(data, variables);
    },
    onError: (error, variables) => {
      if (!silentError) toast.error(errorMessage(error));
      onError?.(error, variables);
    },
  });
}

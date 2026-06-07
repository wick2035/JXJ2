import { useCallback, useEffect, useMemo, useState } from 'react';
import { getCategories } from '../api/category';
import type { CategoryMeta } from '../types';

const FALLBACK_COLOR = '#1677FF';

export const useCategories = () => {
  const [categories, setCategories] = useState<CategoryMeta[]>([]);
  const [loading, setLoading] = useState(false);

  const loadCategories = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getCategories();
      setCategories(res.data.data || []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  const categoryMap = useMemo(
    () => new Map(categories.map((category) => [category.code, category])),
    [categories]
  );

  const getCategoryName = useCallback(
    (code: string) => categoryMap.get(code)?.name || code,
    [categoryMap]
  );

  const getCategoryColor = useCallback(
    (code: string) => categoryMap.get(code)?.color || FALLBACK_COLOR,
    [categoryMap]
  );

  return {
    categories,
    categoryMap,
    loading,
    loadCategories,
    getCategoryName,
    getCategoryColor,
  };
};

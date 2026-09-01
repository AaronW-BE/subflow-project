import { useState, type FormEvent } from 'react';
import { Lock } from 'lucide-react';
import { fieldClass } from '../components/ui';

export function LoginView({
  error,
  onUnlock,
}: {
  error: string;
  onUnlock: (token: string) => void;
}) {
  const [token, setToken] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (token.trim()) onUnlock(token.trim());
  };

  return (
    <div className="min-h-screen bg-[#F2F2F7] text-[#1C1C1E] flex items-center justify-center p-6">
      <form
        onSubmit={submit}
        className="w-full max-w-sm bg-white rounded-2xl border border-[#E5E5EA] p-7 shadow-xs"
      >
        <div className="w-11 h-11 rounded-xl bg-[#5856D6] flex items-center justify-center text-white mb-5">
          <Lock className="w-5 h-5" />
        </div>
        <h1 className="text-xl font-bold tracking-tight">
          SubFlow Admin Console
        </h1>
        <p className="text-sm text-[#8E8E93] mt-1 leading-relaxed">
          Enter the operator token. It is the value of{' '}
          <code className="font-mono text-[#5856D6]">ADMIN_TOKEN</code>, or the
          one printed in the server log at startup.
        </p>
        <label htmlFor="admin-token" className="sr-only">
          Admin token
        </label>
        <input
          id="admin-token"
          type="password"
          value={token}
          onChange={e => setToken(e.target.value)}
          autoFocus
          autoComplete="off"
          placeholder="Admin token"
          className={`${fieldClass} mt-5 bg-white font-mono`}
        />
        {error && (
          <p role="alert" className="text-xs text-[#FF3B30] mt-2.5 leading-relaxed">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={!token.trim()}
          className="w-full mt-5 px-4 py-2.5 rounded-xl bg-[#5856D6] text-white text-sm font-semibold hover:bg-[#4745B8] disabled:opacity-40 transition"
        >
          Unlock
        </button>
      </form>
    </div>
  );
}
